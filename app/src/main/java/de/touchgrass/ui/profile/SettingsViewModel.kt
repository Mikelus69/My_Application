package de.touchgrass.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.AuthSession
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val weight: String = "",
    val goalType: GoalType = GoalType.MINUTES,
    val goalTarget: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val error: String? = null,
)

class SettingsViewModel(private val api: SportApi, private val session: AuthSession) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                showProfile(apiCall { api.me() })
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun onWeightChange(value: String) = edit("weightKg") { copy(weight = value) }

    fun onGoalTypeChange(value: GoalType) = edit("goalType") { copy(goalType = value) }

    fun onGoalTargetChange(value: String) = edit("goalTarget") { copy(goalTarget = value) }

    fun onOldPasswordChange(value: String) = edit("oldPassword") { copy(oldPassword = value) }

    fun onNewPasswordChange(value: String) = edit("newPassword") { copy(newPassword = value) }

    fun saveProfile() {
        val current = _state.value
        val weightText = current.weight.trim()
        val weight = if (weightText.isEmpty()) null else weightText.replace(',', '.').toDoubleOrNull()
            ?: return showFieldError("weightKg", "Bitte eine Zahl eingeben, z. B. 78,5.")
        val target = current.goalTarget.trim().toIntOrNull()
            ?: return showFieldError("goalTarget", "Bitte eine ganze Zahl eingeben.")
        viewModelScope.launch {
            try {
                showProfile(apiCall { api.updateMe(UpdateMeRequest(weight, current.goalType, target)) })
                _state.update { it.copy(message = "Gespeichert.") }
            } catch (e: ApiException) {
                showError(e)
            }
        }
    }

    fun changePassword() {
        val current = _state.value
        viewModelScope.launch {
            try {
                apiCall { api.changePassword(ChangePasswordRequest(current.oldPassword, current.newPassword)) }
                _state.update { it.copy(oldPassword = "", newPassword = "", message = "Passwort geändert.") }
            } catch (e: ApiException) {
                showError(e)
            }
        }
    }

    fun logOut() {
        viewModelScope.launch { session.logOut() }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                apiCall { api.deleteMe() }
                session.logOut()
            } catch (e: ApiException) {
                showError(e)
            }
        }
    }

    private fun showProfile(user: UserDto) = _state.update {
        it.copy(
            loading = false,
            weight = user.weightKg?.let(::formatWeight) ?: "",
            goalType = user.goalType,
            goalTarget = user.goalTarget.toString(),
        )
    }

    private fun edit(field: String, change: SettingsUiState.() -> SettingsUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, message = null, error = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message), message = null) }

    private fun showError(e: ApiException) {
        if (e.field != null) showFieldError(e.field, e.message)
        else _state.update { it.copy(error = e.message, message = null) }
    }
}
