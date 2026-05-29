package com.ai.assistance.shower
import android.os.IBinder; import android.os.Parcel; import android.os.Parcelable
class ShowerBinderContainer(val binder: IBinder) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readStrongBinder()!!)
    override fun writeToParcel(parcel: Parcel, flags: Int) { parcel.writeStrongBinder(binder) }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<ShowerBinderContainer> {
        override fun createFromParcel(parcel: Parcel) = ShowerBinderContainer(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ShowerBinderContainer?>(size)
    }
}
