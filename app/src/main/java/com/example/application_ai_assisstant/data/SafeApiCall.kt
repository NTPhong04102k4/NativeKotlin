package com.example.application_ai_assisstant.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Bọc một lời gọi API thành [Result], xử lý try/catch ĐÚNG MỘT LẦN cho cả app.
 *
 * Nhờ hàm này mà không Repository nào phải viết lại try/catch, và không ViewModel nào
 * phải biết tới HttpException/IOException của tầng mạng.
 *
 * Lưu ý: [CancellationException] phải được ném lại, KHÔNG nuốt — nếu nuốt thì việc huỷ
 * coroutine (ví dụ ViewModel bị clear) sẽ bị hiểu nhầm thành lỗi và hiện toast cho người dùng.
 */
suspend fun <T : Any> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Result.Error(AppException(AppException.Kind.NETWORK, e))
    } catch (e: HttpException) {
        val kind = when (e.code()) {
            401, 403 -> AppException.Kind.UNAUTHORIZED
            else -> AppException.Kind.SERVER
        }
        Result.Error(AppException(kind, e))
    } catch (e: Exception) {
        Result.Error(AppException(AppException.Kind.UNKNOWN, e))
    }
}
