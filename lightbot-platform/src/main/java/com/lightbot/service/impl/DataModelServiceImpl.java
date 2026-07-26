package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelCreateDTO;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.dto.datacenter.DataModelUpdateDTO;
import com.lightbot.entity.DataModel;
import com.lightbot.entity.DataModelCategory;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.DataModelCategoryMapper;
import com.lightbot.mapper.DataModelMapper;
import com.lightbot.service.DataModelService;
import com.lightbot.util.DataModelDdlUtil;
import com.lightbot.util.DataModelSchemaSupport;
import com.lightbot.vo.DataModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据模型服务：元数据 + 物理表 DDL 同步
 *
 * @author finch
 * @since 2026-07-26
 */
@Service
@RequiredArgsConstructor
public class DataModelServiceImpl extends ServiceImpl<DataModelMapper, DataModel>
        implements DataModelService {

    private final DataModelCategoryMapper categoryMapper;
    private final DataModelSchemaSupport schemaSupport;
    private final DataModelDdlUtil ddlUtil;

    @Override
    public List<DataModelVO> listMine(Long categoryId, String keyword) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<DataModel> qw = new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getUserId, userId)
                .eq(categoryId != null, DataModel::getCategoryId, categoryId)
                .orderByDesc(DataModel::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(DataModel::getName, keyword.trim())
                    .or().like(DataModel::getDescription, keyword.trim())
                    .or().like(DataModel::getTableName, keyword.trim()));
        }
        return list(qw).stream().map(this::toVo).toList();
    }

    @Override
    public DataModelVO getMine(Long id) {
        return toVo(requireOwned(id));
    }

    @Override
    public DataModel requireOwned(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        DataModel model = getById(id);
        if (model == null || model.getUserId() == null || model.getUserId() != userId) {
            throw new BizException(ErrorCode.DATA_MODEL_NOT_FOUND);
        }
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO create(DataModelCreateDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        // 1. 校验分类归属
        requireOwnedCategory(dto.getCategoryId(), userId);
        // 2. 表名唯一
        String tableName = schemaSupport.buildTableName(dto.getTableNameSuffix().trim().toLowerCase());
        long tableExists = count(new LambdaQueryWrapper<DataModel>().eq(DataModel::getTableName, tableName));
        if (tableExists > 0 || ddlUtil.tableExists(tableName)) {
            throw new BizException(ErrorCode.DATA_MODEL_TABLE_EXISTS, tableName);
        }
        // 3. 名称唯一（同用户）
        long nameExists = count(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getUserId, userId)
                .eq(DataModel::getName, dto.getName().trim()));
        if (nameExists > 0) {
            throw new BizException(ErrorCode.DATA_MODEL_NAME_EXISTS);
        }
        // 4. 写元数据 + 建空表（含系统字段）
        DataModelSchema empty = schemaSupport.emptySchema();
        DataModel model = new DataModel();
        model.setUserId(userId);
        model.setCategoryId(dto.getCategoryId());
        model.setName(dto.getName().trim());
        model.setTableName(tableName);
        model.setDescription(dto.getDescription());
        model.setSchemaJson(schemaSupport.toSchemaJson(empty));
        save(model);
        ddlUtil.createTable(tableName, empty);
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO updateInfo(Long id, DataModelUpdateDTO dto) {
        DataModel model = requireOwned(id);
        requireOwnedCategory(dto.getCategoryId(), model.getUserId());
        long nameExists = count(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getUserId, model.getUserId())
                .eq(DataModel::getName, dto.getName().trim())
                .ne(DataModel::getId, id));
        if (nameExists > 0) {
            throw new BizException(ErrorCode.DATA_MODEL_NAME_EXISTS);
        }
        model.setName(dto.getName().trim());
        model.setCategoryId(dto.getCategoryId());
        model.setDescription(dto.getDescription());
        updateById(model);
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelVO updateSchema(Long id, DataModelSchema schema) {
        DataModel model = requireOwned(id);
        // 1. 校验 schema
        schemaSupport.validateSchema(schema);
        // 2. 对比并同步物理表 / 索引
        ddlUtil.syncTable(model.getTableName(), schema);
        // 3. 持久化元数据
        model.setSchemaJson(schemaSupport.toSchemaJson(schema));
        updateById(model);
        return toVo(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DataModel model = requireOwned(id);
        removeById(id);
        // 物理表一并清理，避免孤儿表占用表名
        ddlUtil.dropTableIfExists(model.getTableName());
    }

    private void requireOwnedCategory(Long categoryId, Long userId) {
        DataModelCategory category = categoryMapper.selectById(categoryId);
        if (category == null || category.getUserId() == null || !category.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.DATA_CATEGORY_NOT_FOUND);
        }
    }

    private DataModelVO toVo(DataModel model) {
        DataModelVO vo = new DataModelVO();
        vo.setId(model.getId());
        vo.setCategoryId(model.getCategoryId());
        vo.setName(model.getName());
        vo.setTableName(model.getTableName());
        vo.setDescription(model.getDescription());
        vo.setSchema(schemaSupport.parseSchema(model.getSchemaJson()));
        vo.setCreateTime(model.getCreateTime());
        vo.setUpdateTime(model.getUpdateTime());
        return vo;
    }
}
