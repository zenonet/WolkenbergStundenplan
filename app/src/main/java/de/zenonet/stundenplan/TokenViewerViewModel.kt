package de.zenonet.stundenplan

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.zenonet.stundenplan.common.ResultType
import de.zenonet.stundenplan.common.models.User
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TokenViewerViewModel(val timeTableManager: TimeTableManager?, val sharedPrefs: SharedPreferences) : ViewModel() {

    var refreshToken:String? by mutableStateOf(null)
    var accessToken:String? by mutableStateOf(null)
    var accessTokenErrorMessage: String? by mutableStateOf(null)
    var fullName:String by mutableStateOf("Nicht verfügbar")
    var userType:String by mutableStateOf("Nicht verfügbar")
    var studentId by mutableIntStateOf(-1)


    fun loadRefreshToken(){
        refreshToken = sharedPrefs.getString("refreshToken", null);
    }

    fun generateAccessToken() {
        if(timeTableManager == null) return
        accessTokenErrorMessage = ""
        viewModelScope.launch(viewModelScope.coroutineContext){
            val result = withContext(Dispatchers.IO){
                timeTableManager.apiClient.login()
            }

            if(result != ResultType.Success){
                accessTokenErrorMessage = "Acccess-Token konnte nicht geladen werden: ${result.name}"
                return@launch
            }

            accessToken = timeTableManager.apiClient.accessToken
        }
    }
    fun loadUserData(){
        if(timeTableManager == null) return;
        val user:User? = try {
            timeTableManager.getUser()
        }catch (e: UserLoadException){
            null
        }
        fullName = user?.fullName ?: "Nicht verfügbar"
        studentId = user?.id ?: -1
        userType = user?.type?.name ?: "Nicht verfügbar"
    }
}