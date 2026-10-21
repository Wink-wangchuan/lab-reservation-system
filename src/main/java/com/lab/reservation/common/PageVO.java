package com.lab.reservation.common;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * 通用分页返回对象
 *
 * <p>统一分页 VO，不把 IPage 直接暴露给前端，
 * 同时支持对实体列表做 DTO/VO 转换（mapTo 方法）。</p>
 *
 * @param <T> 分页内数据元素类型（一般为 VO）
 * @author LabWise
 * @since 1.0.0
 */
@Data
public class PageVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private Long total;

    /** 每页条数 */
    private Long pageSize;

    /** 当前页码 */
    private Long current;

    /** 总页数 */
    private Long pages;

    /** 数据列表 */
    @JsonAlias("records")
    private List<T> records;

    /** 是否还有下一页 */
    private Boolean hasNext;

    public PageVO() {
    }

    /**
     * 构造分页对象（自动计算总页数与 isHasNext）
     *
     * @param source    MyBatis-Plus 分页结果
     * @param converter 实体 -> VO 转换器
     */
    public static <T, R> PageVO<R> of(IPage<T> source, Function<T, R> converter) {
        PageVO<R> vo = new PageVO<>();
        vo.setTotal(source.getTotal());
        vo.setPageSize(source.getSize());
        vo.setCurrent(source.getCurrent());
        vo.setPages(source.getPages());
        vo.setHasNext(source.getCurrent() < source.getPages());
        vo.setRecords(source.getRecords() == null
                ? List.of()
                : source.getRecords().stream().map(converter).toList());
        return vo;
    }

    /**
     * 构造分页对象（实体字段与 VO 同名时的便捷转换）
     */
    public static <T, R> PageVO<R> of(IPage<T> source, Class<R> voClass) {
        // 两参重载明确返回 T，规避三参重载在泛型推导时被解析为 void
        Function<T, R> converter = entity -> BeanUtil.copyProperties(entity, voClass);
        return of(source, converter);
    }
}