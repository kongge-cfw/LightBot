package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.dto.datacenter.DataModelCategorySaveDTO;
import com.lightbot.entity.DataModel;
import com.lightbot.entity.DataModelCategory;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.DataModelCategoryMapper;
import com.lightbot.mapper.DataModelMapper;
import com.lightbot.service.DataModelCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据模型分类服务实现
 *
 * @author finch
 * @since 2026-07-26
 */
@Service
@RequiredArgsConstructor
public class DataModelCategoryServiceImpl extends ServiceImpl<DataModelCategoryMapper, DataModelCategory>
        implements DataModelCategoryService {

    private final DataModelMapper dataModelMapper;

    @Override
    public List<DataModelCategory> listMine() {
        long userId = StpUtil.getLoginIdAsLong();
        return list(new LambdaQueryWrapper<DataModelCategory>()
                .eq(DataModelCategory::getUserId, userId)
                .orderByAsc(DataModelCategory::getSortOrder)
                .orderByAsc(DataModelCategory::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelCategory create(DataModelCategorySaveDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        // 1. 同用户下名称唯一
        long exists = count(new LambdaQueryWrapper<DataModelCategory>()
                .eq(DataModelCategory::getUserId, userId)
                .eq(DataModelCategory::getName, dto.getName().trim()));
        if (exists > 0) {
            throw new BizException(ErrorCode.DATA_CATEGORY_NAME_EXISTS);
        }
        // 2. 保存
        DataModelCategory category = new DataModelCategory();
        category.setUserId(userId);
        category.setName(dto.getName().trim());
        category.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        save(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataModelCategory update(Long id, DataModelCategorySaveDTO dto) {
        DataModelCategory category = requireOwned(id);
        long userId = category.getUserId();
        long exists = count(new LambdaQueryWrapper<DataModelCategory>()
                .eq(DataModelCategory::getUserId, userId)
                .eq(DataModelCategory::getName, dto.getName().trim())
                .ne(DataModelCategory::getId, id));
        if (exists > 0) {
            throw new BizException(ErrorCode.DATA_CATEGORY_NAME_EXISTS);
        }
        category.setName(dto.getName().trim());
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        updateById(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DataModelCategory category = requireOwned(id);
        Long modelCount = dataModelMapper.selectCount(new LambdaQueryWrapper<DataModel>()
                .eq(DataModel::getCategoryId, category.getId())
                .eq(DataModel::getUserId, category.getUserId()));
        if (modelCount != null && modelCount > 0) {
            throw new BizException(ErrorCode.DATA_CATEGORY_HAS_MODELS);
        }
        removeById(id);
    }

    private DataModelCategory requireOwned(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        DataModelCategory category = getById(id);
        if (category == null || !userIdEquals(category.getUserId(), userId)) {
            throw new BizException(ErrorCode.DATA_CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private boolean userIdEquals(Long a, long b) {
        return a != null && a == b;
    }
}
