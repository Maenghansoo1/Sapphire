package com.sapphire.hawe.feature.groups

import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.LocalDate

data class CreateGroupState(
    val name: String = "", val relationship: Relationship = Relationship.FRIEND,
    val selectedDate: LocalDate,
) {
    val canCreate: Boolean get() = name.trim().isNotEmpty()
    val showsStartDate: Boolean get() = relationship == Relationship.LOVER
    val maxMembers: Int get() = relationship.maxMembers
    val startDateForCreation: LocalDate? get() = selectedDate.takeIf { showsStartDate }
    val subtitle: String get() = if (showsStartDate) "만든 뒤 초대 코드로 연인 1명을 초대할 수 있어요."
        else "만든 뒤 초대 코드로 최대 ${maxMembers - 1}명을 더 초대할 수 있어요."
    fun withName(input: String): CreateGroupState = copy(name = input.take(16))
}
