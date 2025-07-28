package service.accessibility.bridge

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable

data class ViewNode(
    val className: String,
    val text: String,
    val resourceID: Int,
    val uniqueID: String,
    val bounds: Rect,
    val children: List<ViewNode>,
    val packageName: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "Unknown",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "",
        Rect.CREATOR.createFromParcel(parcel),
        parcel.createTypedArrayList(CREATOR) ?: emptyList(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(className)
        parcel.writeString(text)
        parcel.writeInt(resourceID)
        parcel.writeString(uniqueID)
        bounds.writeToParcel(parcel, flags)
        parcel.writeTypedList(children)
        parcel.writeString(packageName)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ViewNode> {
        override fun createFromParcel(parcel: Parcel): ViewNode {
            return ViewNode(parcel)
        }

        override fun newArray(size: Int): Array<ViewNode?> {
            return arrayOfNulls(size)
        }
    }
}

data class Finger(
    val id: Int,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val duration: Long = 300L,
    val keepDown: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeInt(x1)
        parcel.writeInt(y1)
        parcel.writeInt(x2)
        parcel.writeInt(y2)
        parcel.writeLong(duration)
        parcel.writeByte(if (keepDown) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Finger> {
        override fun createFromParcel(parcel: Parcel): Finger {
            return Finger(parcel)
        }

        override fun newArray(size: Int): Array<Finger?> {
            return arrayOfNulls(size)
        }
    }
}
