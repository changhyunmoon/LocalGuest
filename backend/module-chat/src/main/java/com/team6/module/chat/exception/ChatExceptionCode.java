    package com.team6.module.chat.exception;


    import com.team6.module.common.global.exception.ExceptionCode;
    import lombok.Getter;
    import lombok.RequiredArgsConstructor;
    import org.springframework.http.HttpStatus;

    import static org.springframework.http.HttpStatus.NOT_FOUND;

    @Getter
    @RequiredArgsConstructor
    public enum ChatExceptionCode implements ExceptionCode {
        PARTICIPANT_NOT_FOUND(NOT_FOUND, "채팅방에서 사용자를 찾을 수 없습니다."),
        CHATROOM_NOT_FOUND(NOT_FOUND, "채팅방을 찾을 수 없습니다."),
        ;

        private final HttpStatus status;

        private final String message;

        @Override
        public String getCode() {
            return this.name();
        }

    }