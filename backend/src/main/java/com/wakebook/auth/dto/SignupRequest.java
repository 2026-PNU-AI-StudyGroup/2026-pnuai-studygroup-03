package com.wakebook.auth.dto;

import com.wakebook.user.domain.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotNull(message = "역할을 선택해 주세요.")
        UserRole role,

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password,

        @Size(max = 100, message = "닉네임은 100자 이하여야 합니다.")
        String nickname,

        @Size(max = 200, message = "소속 도서관은 200자 이하여야 합니다.")
        String libraryName,

        @Size(max = 20, message = "도서관 코드는 20자 이하여야 합니다.")
        String libraryCode,

        @Size(max = 100, message = "담당 부서는 100자 이하여야 합니다.")
        String department
) {
    @AssertTrue(message = "사서는 소속 도서관, 도서관 코드, 담당 부서를 입력해야 합니다.")
    public boolean isLibrarianInformationValid() {
        if (role != UserRole.LIBRARIAN) {
            return true;
        }
        return hasText(libraryName) && hasText(libraryCode) && hasText(department);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "SignupRequest[" +
                "role=" + role +
                ", name=" + name +
                ", email=" + email +
                ", password=[REDACTED]" +
                ", nickname=" + nickname +
                ", libraryName=" + libraryName +
                ", libraryCode=" + libraryCode +
                ", department=" + department +
                ']';
    }
}
