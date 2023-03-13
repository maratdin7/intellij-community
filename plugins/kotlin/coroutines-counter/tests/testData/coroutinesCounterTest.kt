import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope

fun <lineMarker descr = "1" > foo < / lineMarker >() {
    GlobalScope.launch { }
}

fun <lineMarker descr = "0" > bar < / lineMarker >() {
}

fun <lineMarker descr = "2" > x < / lineMarker >() {
    GlobalScope.launch { }
    GlobalScope.launch { }
}

fun <lineMarker descr = "3" > y < / lineMarker >() {
    GlobalScope.launch {
        x()
    }
}

fun <lineMarker descr = "0" > f < / lineMarker >(a: Int) {
    val x = ::f
}

fun <lineMarker descr = "Infinity" > infinity1 < / lineMarker >() {
    GlobalScope.launch { infinity2() }
}

fun <lineMarker descr = "Infinity" > infinity2 < / lineMarker >() {
    GlobalScope.launch { infinity1() }
}

class A {
    fun <T> <lineMarker descr ="3">foo</lineMarker>(list: List<T>)
    {
        list.run {
            GlobalScope.launch { x() }
        }
    }

    fun <lineMarker descr = "0" > bar < / lineMarker >(): () -> Job
    {
        val l = { GlobalScope.launch { } }

        return l
    }
}

suspend fun <lineMarker descr = "1" > cond1 < / lineMarker >(b: Boolean) {
    <lineMarker descr ="Suspend function call 'coroutineScope()'">coroutineScope</lineMarker> {
    if (b) launch { }
    else launch { }
}
}

suspend fun <lineMarker descr = "Some coroutines could be launched" > cond2 < / lineMarker >(b: Boolean) {
    <lineMarker descr ="Suspend function call 'coroutineScope()'">coroutineScope</lineMarker> {
    if (b) launch { x() }
    else launch { }
}
}

fun <T> <lineMarker descr ="Some coroutines could be launched">loop</lineMarker>(list: List<T>) {
    repeat(list.size) {
        GlobalScope.launch { }
    }
}

fun <lineMarker descr = "0" > f < / lineMarker >(a: Int) {
    val x = ::f
}

fun <lineMarker descr = "Some coroutines could be launched" > e < / lineMarker >() {
    fun <lineMarker descr = "1" > ref < / lineMarker >() {
        GlobalScope.launch { }
    }

    q(::ref)
}

fun <lineMarker descr = "Some coroutines could be launched" > q < / lineMarker >(block:() -> Unit) {
    block()
}

fun <lineMarker descr = "1" > r < / lineMarker >() {
    fun <lineMarker descr = "1" > local < / lineMarker >() {
        GlobalScope.launch { }
    }

    local()
}






