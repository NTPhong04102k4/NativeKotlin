package com.example.application_ai_assisstant.data

/**
 * Lỗi đã được phân loại của tầng data.
 *
 * Tầng data KHÔNG biết `R.string` — nó chỉ nói "lỗi thuộc loại gì".
 * Việc dịch [kind] thành câu chữ hiển thị là việc của tầng UI (xem `ui/common/ErrorMessages.kt`).
 */
class AppException(
    val kind: Kind,
    cause: Throwable? = null,
) : Exception(kind.name, cause) {

    enum class Kind {
        /** Mất mạng, DNS lỗi, timeout. */
        NETWORK,

        /** Server trả 5xx hoặc 4xx khác 401. */
        SERVER,

        /** HTTP 401/403 — token hết hạn, cần đăng nhập lại. */
        UNAUTHORIZED,

        /** Parse JSON lỗi hoặc bất kỳ exception nào không lường trước. */
        UNKNOWN,
    }
}
