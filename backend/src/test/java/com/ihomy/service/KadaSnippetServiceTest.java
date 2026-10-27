package com.ihomy.service;

import com.ihomy.entity.KadaSnippet;
import com.ihomy.mapper.KadaSnippetMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核动作:通过时写文件 + 重建索引;驳回已上架时删文件并重建索引;重复审核幂等。
 * 文件写入用 mock,只验调用顺序与索引内容(真实原子写由 FileService 负责)。
 */
@ExtendWith(MockitoExtension.class)
class KadaSnippetServiceTest {

    @Mock
    private KadaSnippetMapper mapper;
    @Mock
    private FileService fileService;
    @InjectMocks
    private KadaSnippetService service;

    /** 纯单测没有 Spring 启动流程,手动初始化实体的 lambda 列缓存。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KadaSnippet.class);
    }

    @Test
    void approveWritesFileAndCommunityIndex() {
        KadaSnippet row = pending("{\"shortcuts\":[{\"triggers\":[\"F1\"],\"actions\":[{\"type\":\"command\",\"shell\":\"cmd\",\"command\":\"whoami\"}]}],\"kada_snippet\":{\"version\":1,\"name\":\"危险包\",\"author\":\"小明\"}}");
        when(mapper.selectById(1L)).thenReturn(row);
        when(fileService.saveKadaSnippet(1L, row.getContentJson())).thenReturn("/files/kada/snippets/community/1.json");
        when(mapper.selectList(any())).thenReturn(List.of(row));

        KadaSnippet out = service.approve(1L, 9L, "看过动作了");

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getDangerLevel()).isEqualTo(3);
        assertThat(out.getPublishedUrl()).isEqualTo("/files/kada/snippets/community/1.json");
        ArgumentCaptor<String> index = ArgumentCaptor.forClass(String.class);
        verify(fileService).saveKadaCommunityIndex(index.capture());
        assertThat(index.getValue())
                .contains("\"name\":\"测试卡带\"")
                .contains("\"url\":\"https://ihomy.top/files/kada/snippets/community/1.json\"")
                .contains("\"level\":3");
        verify(mapper).updateById(row);
    }

    @Test
    void rejectApprovedDeletesFileAndRebuildsIndex() {
        KadaSnippet row = pending("{\"expansions\":[{\"trigger\":\";a\",\"replace\":\"x\"}],\"kada_snippet\":{\"version\":1,\"name\":\"旧包\"}}");
        row.setStatus("APPROVED");
        row.setPublishedUrl("/files/kada/snippets/community/2.json");
        when(mapper.selectById(2L)).thenReturn(row);
        when(mapper.selectList(any())).thenReturn(List.of());

        KadaSnippet out = service.reject(2L, 9L, "");

        assertThat(out.getStatus()).isEqualTo("REJECTED");
        assertThat(out.getPublishedUrl()).isEmpty();
        assertThat(out.getReviewNote()).isEqualTo("未通过人工审核");
        verify(fileService).deleteKadaSnippet(2L);
        verify(fileService).saveKadaCommunityIndex(anyString());
    }

    @Test
    void approvingAgainIsIdempotent() {
        KadaSnippet row = pending("{\"expansions\":[{\"trigger\":\";a\",\"replace\":\"x\"}]}");
        row.setStatus("APPROVED");
        when(mapper.selectById(3L)).thenReturn(row);

        service.approve(3L, 9L, "");

        verify(fileService, never()).saveKadaSnippet(anyLong(), any());
        verify(mapper, never()).updateById(any(KadaSnippet.class));
    }

    private KadaSnippet pending(String content) {
        KadaSnippet row = new KadaSnippet();
        row.setId(1L);
        row.setName("测试卡带");
        row.setAuthor("小明");
        row.setTags("[]");
        row.setContentJson(content);
        row.setDangerLevel(0);
        row.setFindingsJson("[]");
        row.setLegalFlagsJson("[]");
        row.setStatus(KadaSnippetService.PENDING);
        return row;
    }
}
