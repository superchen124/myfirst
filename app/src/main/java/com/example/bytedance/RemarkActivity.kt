package com.example.bytedance

import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RemarkActivity : AppCompatActivity() {

    private lateinit var tvNickname: TextView
    private lateinit var etRemark: EditText
    private lateinit var btnSave: Button
    private lateinit var btnExit: Button
    private lateinit var dbHelper: MessageDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置进入和退出转场动画
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.allowEnterTransitionOverlap = true
        window.allowReturnTransitionOverlap = true
        
        // 进入动画：渐变 + 卡片放大
        window.enterTransition = android.transition.Fade().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // 退出动画：跟手下滑
        window.exitTransition = android.transition.Slide(android.view.Gravity.BOTTOM).apply {
            duration = 250
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        
        // 共享元素进入：头像放大
        window.sharedElementEnterTransition = android.transition.ChangeBounds().apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // 共享元素退出：头像缩小
        window.sharedElementExitTransition = android.transition.ChangeBounds().apply {
            duration = 250
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        
        setContentView(R.layout.activity_remark)
        
        // 设置共享元素转场名称
        val container = findViewById<View>(R.id.container)
        ViewCompat.setTransitionName(container, getString(R.string.transition_card))

        dbHelper = MessageDatabaseHelper(this)

        tvNickname = findViewById(R.id.tvNickname)
        etRemark = findViewById(R.id.etRemark)
        btnSave = findViewById(R.id.btnSave)
        btnExit = findViewById(R.id.btnExit)

        val userName = intent.getStringExtra(EXTRA_USER_NAME) ?: ""
        tvNickname.text = userName

        // 进入页面时，读取并展示已有备注
        val existingRemark = dbHelper.getRemark(userName)
        etRemark.setText(existingRemark ?: "")

        btnSave.setOnClickListener {
            val remark = etRemark.text.toString()
            dbHelper.saveRemark(userName, remark)
            finishAfterTransition() // 使用转场动画退出
        }

        btnExit.setOnClickListener {
            finishAfterTransition() // 使用转场动画退出（跟手下滑）
        }
        
        // 添加进入动画：渐变 + 卡片放大（延迟启动，与窗口转场协调）
        container.post {
            container.alpha = 0f
            container.scaleX = 0.9f
            container.scaleY = 0.9f
            container.translationY = 50f
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(100) // 延迟启动，让窗口转场先开始
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finishAfterTransition() // 返回键也使用转场动画
    }

    companion object {
        const val EXTRA_USER_NAME = "extra_user_name"
    }
}




