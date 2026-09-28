package org.apidependabot.answer;

import org.apidependabot.retrieval.LexicalRetrievalService.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VanillaRagAnswerServiceTest {
    @Test
    void makesOneModelCallAndReturnsEvidenceMap() {
        ChatModel chatModel = mock(ChatModel.class);
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("The consumer reads the name through getName() [E1].")))));
        VanillaRagAnswerService service = new VanillaRagAnswerService(provider);
        RetrievedChunk chunk = new RetrievedChunk("src/main/java/demo/WidgetService.java", 4, 8,
                "java-source", "return widget.getName();", 0.7);

        VanillaRagAnswerService.Answer answer = service.answer("Where is name used?", "Removed name.", List.of(chunk));

        assertThat(answer.text()).contains("[E1]");
        assertThat(answer.evidence()).containsExactly(
                new VanillaRagAnswerService.EvidenceReference("E1", "src/main/java/demo/WidgetService.java", 4, 8));
        assertThat(answer.latencyMillis()).isGreaterThanOrEqualTo(0);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }
}
