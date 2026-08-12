package com.example.application_ai_assisstant.ui.common

import androidx.annotation.StringRes
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.data.AppException
import com.example.application_ai_assisstant.data.model.DiscoveryCategory

/**
 * Biên giới giữa tầng data và chuỗi hiển thị.
 *
 * Tầng data chỉ phân loại lỗi; việc chọn câu chữ nào (và dịch sang ngôn ngữ nào)
 * xảy ra ở đúng một chỗ này. Lint `HardcodedText` đang ở mức error nên mọi chuỗi
 * hiển thị đều phải đi qua strings.xml.
 */
@StringRes
fun AppException.Kind.toMessageRes(): Int = when (this) {
    AppException.Kind.NETWORK -> R.string.error_network
    AppException.Kind.SERVER -> R.string.error_server
    AppException.Kind.UNAUTHORIZED -> R.string.error_unauthorized
    AppException.Kind.UNKNOWN -> R.string.error_unknown
}

@StringRes
fun DiscoveryCategory.toLabelRes(): Int = when (this) {
    DiscoveryCategory.TECHNOLOGY -> R.string.filter_technology
    DiscoveryCategory.HEALTH -> R.string.filter_health
    DiscoveryCategory.LIFE -> R.string.filter_life
}
