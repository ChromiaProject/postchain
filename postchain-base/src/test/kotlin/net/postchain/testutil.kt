package net.postchain

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class DynamicValueAnswer<T>(var value: T) : Answer<T> {
    override fun answer(p0: InvocationOnMock?): T = value
}
