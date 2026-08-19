package com.wakebook.curation;

import com.wakebook.curation.domain.Curation;
import com.wakebook.curation.repository.CurationRepository;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurationPublicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurationRepository curationRepository;

    @Test
    void 인증_없이_공개_큐레이션만_목록으로_조회한다() throws Exception {
        User librarian = saveLibrarian("public-list@wakebook.kr");
        curationRepository.save(new Curation(librarian, "공개 큐레이션", "공개 설명", true));
        curationRepository.save(new Curation(librarian, "비공개 큐레이션", "비공개 설명", false));

        mockMvc.perform(get("/api/curations")
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].title").value("공개 큐레이션"));
    }

    @Test
    void 인증_없이_공개_큐레이션_상세를_조회한다() throws Exception {
        User librarian = saveLibrarian("public-detail@wakebook.kr");
        Curation curation = curationRepository.saveAndFlush(
                new Curation(librarian, "공개 큐레이션", "공개 설명", true)
        );

        mockMvc.perform(get("/api/curations/{curationId}", curation.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(curation.getId()))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    void 비공개_큐레이션_상세는_존재를_노출하지_않는다() throws Exception {
        User librarian = saveLibrarian("private-detail@wakebook.kr");
        Curation curation = curationRepository.saveAndFlush(
                new Curation(librarian, "비공개 큐레이션", "비공개 설명", false)
        );

        mockMvc.perform(get("/api/curations/{curationId}", curation.getId())
                        .contextPath("/api"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURATION_001"));
    }

    private User saveLibrarian(String email) {
        return userRepository.saveAndFlush(new User(
                UserRole.LIBRARIAN,
                "김도서",
                email,
                "encoded-password",
                "책지기",
                "부산광역시 금정도서관",
                "121018",
                "자료운영팀"
        ));
    }
}
