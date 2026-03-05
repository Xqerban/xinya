package com.xinya.dtx.core.database.converter

import androidx.room.TypeConverter
import com.xinya.dtx.clinical.ClinicalStage

/**
 * Room 类型转换器
 */
class Converters {
    
    @TypeConverter
    fun fromClinicalStage(stage: ClinicalStage): String {
        return stage.name
    }
    
    @TypeConverter
    fun toClinicalStage(value: String): ClinicalStage {
        return ClinicalStage.valueOf(value)
    }
}
