package lavaplayer.demo

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

var <T> AtomicReference<T>.mutableValue: T
    get() = get()
    set(value) = set(value)

operator fun <T> AtomicReference<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return get()
}

operator fun <T> AtomicReference<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    set(value)
}

operator fun AtomicBoolean.getValue(thisRef: Any?, property: KProperty<*>): Boolean {
    return get()
}

operator fun AtomicBoolean.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    set(value)
}
