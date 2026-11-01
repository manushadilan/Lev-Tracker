package com.example.leavetracker

import android.os.Parcel
import android.os.Parcelable
import com.google.android.material.datepicker.CalendarConstraints
import java.util.Calendar
import java.util.TimeZone

// Date picker validator: Saturdays and Sundays cannot be selected
class WeekdayValidator : CalendarConstraints.DateValidator {
    override fun isValid(date: Long): Boolean {
        val d = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }.get(Calendar.DAY_OF_WEEK)
        return d != Calendar.SATURDAY && d != Calendar.SUNDAY
    }
    override fun describeContents() = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun equals(other: Any?) = other is WeekdayValidator
    override fun hashCode() = 7

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<WeekdayValidator> {
            override fun createFromParcel(p: Parcel) = WeekdayValidator()
            override fun newArray(n: Int) = arrayOfNulls<WeekdayValidator>(n)
        }
    }
}
