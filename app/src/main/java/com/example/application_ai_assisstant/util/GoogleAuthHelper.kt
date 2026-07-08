package com.example.application_ai_assisstant.util

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.application_ai_assisstant.R

class GoogleAuthHelper(context: Context) {
    
    val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // .requestIdToken(context.getString(R.string.default_web_client_id)) // Cần cấu hình Firebase/Google Console
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
}
