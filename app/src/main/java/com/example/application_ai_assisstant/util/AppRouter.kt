package com.example.application_ai_assisstant.util

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.ui.discovery.DiscoveryActivity
import com.example.application_ai_assisstant.ui.login.LoginActivity
import com.example.application_ai_assisstant.ui.personal.PersonalActivity
import com.example.application_ai_assisstant.ui.schedule.ScheduleActivity
import com.example.application_ai_assisstant.ui.settings.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Router tập trung của app. Đây là NƠI DUY NHẤT được phép `startActivity`.
 *
 * Dự án không dùng Fragment/Navigation Component: mỗi màn hình là một Activity riêng. Router này
 * đóng vai trò "nav graph" viết tay — muốn biết app đi được những đâu thì đọc file này.
 *
 * ## Cách các tab giữ được state
 *
 * Ba tab dùng [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT] chứ không `finish()` Activity hiện tại:
 * nếu tab đích đã nằm trong task thì nó được kéo lên trên cùng mà KHÔNG bị tạo lại, nên scroll
 * position, dữ liệu đã nhập, RecyclerView state... đều còn nguyên. Chỉ lần mở đầu tiên mới thực
 * sự tạo Activity mới. Kết hợp với `android:launchMode="singleTop"` trong manifest để chắc chắn
 * không bao giờ có hai instance của cùng một tab.
 *
 * ## Back stack
 *
 * Vì REORDER_TO_FRONT làm thứ tự stack bị xáo, không thể để hệ thống tự xử lý Back. Hợp đồng của
 * bottom navigation (theo Material guideline) được cài đặt tường minh trong [setupTabBackBehavior]:
 * Back ở tab phụ thì quay về [START_TAB_ID], Back ở tab gốc mới thoát app.
 *
 * ## Thêm một tab mới
 *
 * 1. Thêm một dòng vào [TABS]
 * 2. Thêm `<item>` cùng id vào `res/menu/bottom_nav_menu.xml`
 * 3. Khai báo `<activity>` (kèm `launchMode="singleTop"`) trong `AndroidManifest.xml`
 *
 * Không cần sửa gì trong ba Activity còn lại.
 */
object AppRouter {

    /** Tab gốc: nơi Back từ các tab khác quay về, và là màn hình vào app sau khi đăng nhập. */
    val START_TAB_ID: Int = R.id.nav_discovery

    /** Bảng route của bottom navigation: id trong menu -> Activity tương ứng. */
    private val TABS: Map<Int, Class<out Activity>> = linkedMapOf(
        R.id.nav_discovery to DiscoveryActivity::class.java,
        R.id.nav_schedule to ScheduleActivity::class.java,
        R.id.nav_personal to PersonalActivity::class.java,
    )

    // region Điều hướng giữa các tab

    /**
     * Mở tab ứng với [itemId]. Trả về `false` nếu id không thuộc bảng route (khi đó
     * BottomNavigationView sẽ không đánh dấu item được chọn).
     */
    fun openTab(from: Activity, itemId: Int): Boolean {
        val target = TABS[itemId] ?: return false
        if (from.javaClass == target) return true

        from.startActivity(
            Intent(from, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        from.disableTransitionForLegacyApi()
        return true
    }

    // endregion

    // region Điều hướng ra/vào luồng đăng nhập

    /**
     * Vào app sau khi đăng nhập thành công. Xoá sạch task để Back từ màn hình chính không quay
     * ngược lại được LoginActivity.
     */
    fun openHomeAfterLogin(from: Activity) {
        from.startActivity(
            Intent(from, DiscoveryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        from.finish()
    }

    /**
     * Đăng xuất: quay về LoginActivity và xoá toàn bộ back stack, tránh việc Back đưa người dùng
     * trở lại màn hình đã đăng nhập.
     *
     * Router CHỈ điều hướng. Việc xoá token và cache là của `SessionRepository.logout()` —
     * ViewModel gọi nó trước rồi mới phát event yêu cầu chuyển màn.
     */
    fun logout(from: Activity) {
        from.startActivity(
            Intent(from, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        from.finish()
    }

    // endregion

    // region Màn hình phụ (push bình thường, Back quay lại màn trước)

    fun openSettings(from: Activity) {
        from.startActivity(Intent(from, SettingsActivity::class.java))
    }

    // endregion

    /**
     * Cài đặt hành vi Back cho một tab. Tab gốc không đăng ký callback nào để hệ thống xử lý mặc
     * định (thoát app).
     */
    private fun setupTabBackBehavior(activity: AppCompatActivity, currentItemId: Int) {
        if (currentItemId == START_TAB_ID) return
        activity.onBackPressedDispatcher.addCallback(activity) {
            openTab(activity, START_TAB_ID)
        }
    }

    /**
     * Tắt animation chuyển tab trên API < 34.
     *
     * Từ API 34 `overridePendingTransition` bị deprecate và không còn tác dụng; bản thay thế
     * [Activity.overrideActivityTransition] phải được gọi trong `onCreate` của Activity ĐƯỢC MỞ,
     * chứ không phải sau `startActivity` — việc đó do [applyTabTransition] lo.
     */
    @Suppress("DEPRECATION")
    private fun Activity.disableTransitionForLegacyApi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0)
        }
    }

    /** Phần API 34+ của việc tắt animation. Gọi trong `onCreate` của từng tab. */
    private fun Activity.applyTabTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }

    /**
     * Nối [bottomNavigationView] vào router. Gọi trong `onCreate` của mỗi tab với [currentItemId]
     * là id menu của chính tab đó.
     */
    fun bind(
        activity: AppCompatActivity,
        bottomNavigationView: BottomNavigationView,
        currentItemId: Int,
    ) {
        // Phải gán selectedItemId TRƯỚC khi set listener, nếu không việc gán này sẽ tự kích hoạt
        // listener và điều hướng sang chính màn hình đang mở.
        bottomNavigationView.selectedItemId = currentItemId

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == currentItemId) true else openTab(activity, item.itemId)
        }

        // Chạm lại tab đang mở: nuốt sự kiện. Chỗ này để cắm scroll-to-top sau khi có RecyclerView.
        bottomNavigationView.setOnItemReselectedListener { }

        activity.applyTabTransition()
        setupTabBackBehavior(activity, currentItemId)
    }
}

/** Đường tắt cho [AppRouter.bind] để chỗ gọi trong Activity đọc gọn hơn. */
fun AppCompatActivity.setupBottomNavigation(bottomNavigationView: BottomNavigationView, currentItemId: Int) {
    AppRouter.bind(this, bottomNavigationView, currentItemId)
}
