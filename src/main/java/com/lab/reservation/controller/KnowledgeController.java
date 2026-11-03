package com.lab.reservation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.reservation.aop.OperationLog;
import com.lab.reservation.common.PageVO;
import com.lab.reservation.common.Result;
import com.lab.reservation.dto.KnowledgeDTO;
import com.lab.reservation.entity.AiKnowledge;
import com.lab.reservation.service.KnowledgeService;
import com.lab.reservation.vo.KnowledgeHitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 知识库后台管理接口
 *
 * <p>此前知识条目只能改 SQL，这里补齐管理页面所需的增删改查、启停、
 * 命中测试与 JSON 导入导出；所有写操作都会同步失效 AI 检索缓存。</p>
 *
 * @author LabWise
 * @since 4.6.0
 */
@Tag(name = "AI 知识库", description = "知识条目管理 / 命中测试 / 导入导出")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "知识条目分页")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<PageVO<AiKnowledge>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        IPage<AiKnowledge> page = knowledgeService.page(
                new Page<>(current, size), keyword, category, status);
        return Result.success(PageVO.of(page, k -> k));
    }

    @Operation(summary = "知识分类清单")
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<List<String>> categories() {
        return Result.success(knowledgeService.categories());
    }

    @Operation(summary = "新增知识条目")
    @PostMapping
    @OperationLog(module = "AI知识库", businessType = "insert", description = "新增知识条目")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<AiKnowledge> create(@Valid @RequestBody KnowledgeDTO dto) {
        return Result.success("新增成功", knowledgeService.create(dto));
    }

    @Operation(summary = "编辑知识条目")
    @PutMapping("/{id}")
    @OperationLog(module = "AI知识库", businessType = "update", description = "编辑知识条目")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<AiKnowledge> update(@PathVariable Long id, @Valid @RequestBody KnowledgeDTO dto) {
        return Result.success("保存成功", knowledgeService.update(id, dto));
    }

    @Operation(summary = "删除知识条目")
    @DeleteMapping("/{id}")
    @OperationLog(module = "AI知识库", businessType = "delete", description = "删除知识条目")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.success("已删除", null);
    }

    @Operation(summary = "启用/停用知识条目")
    @PutMapping("/{id}/status")
    @OperationLog(module = "AI知识库", businessType = "update", description = "切换知识条目状态")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        knowledgeService.setStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @Operation(summary = "命中测试：验证某句提问会命中哪些知识")
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<List<KnowledgeHitVO>> test(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(knowledgeService.test(question, limit));
    }

    @Operation(summary = "导出全部知识条目（JSON）")
    @GetMapping("/export")
    @OperationLog(module = "AI知识库", businessType = "export", description = "导出知识库")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<List<AiKnowledge>> export() {
        return Result.success(knowledgeService.exportAll());
    }

    @Operation(summary = "批量导入知识条目（JSON，按 ID 覆盖或新增）")
    @PostMapping("/import")
    @OperationLog(module = "AI知识库", businessType = "import", description = "导入知识库")
    @PreAuthorize("hasAuthority('system:knowledge:manage')")
    public Result<Integer> importBatch(@RequestBody List<KnowledgeDTO> items) {
        int written = knowledgeService.importBatch(items);
        return Result.success("已导入 " + written + " 条", written);
    }
}
