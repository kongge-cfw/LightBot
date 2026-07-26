package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.datacenter.DataModelCreateDTO;
import com.lightbot.dto.datacenter.DataModelSchema;
import com.lightbot.dto.datacenter.DataModelUpdateDTO;
import com.lightbot.entity.DataModel;
import com.lightbot.vo.DataModelVO;

import java.util.List;

/**
 * 数据模型元数据服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface DataModelService extends IService<DataModel> {

    List<DataModelVO> listMine(Long categoryId, String keyword);

    DataModelVO getMine(Long id);

    /**
     * 供数据池内部加载（校验归属）
     */
    DataModel requireOwned(Long id);

    DataModelVO create(DataModelCreateDTO dto);

    DataModelVO updateInfo(Long id, DataModelUpdateDTO dto);

    /**
     * 保存 schema 并对比同步物理表结构/索引
     */
    DataModelVO updateSchema(Long id, DataModelSchema schema);

    void delete(Long id);
}
