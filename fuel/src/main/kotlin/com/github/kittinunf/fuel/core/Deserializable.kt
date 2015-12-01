package com.github.kittinunf.fuel.core

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.get
import com.github.kittinunf.result.getAs
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Created by Kittinun Vantasin on 8/16/15.
 */

public interface Deserializable<out T: Any> {

    fun deserialize(response: Response): T

}

public interface ResponseDeserializable<out T : Any> : Deserializable<T> {

    override fun deserialize(response: Response): T {
        return deserialize(response.data) ?:
                deserialize(ByteArrayInputStream(response.data)) ?:
                deserialize(InputStreamReader(ByteArrayInputStream(response.data))) ?:
                deserialize(String(response.data)) ?:
                throw IllegalStateException("One of deserialize(ByteArray) or deserialize(InputStream) or deserialize(Reader) or deserialize(String) must be implemented")
    }

    //One of these methods must be implemented
    public fun deserialize(bytes: ByteArray): T? = null

    public fun deserialize(inputStream: InputStream): T? = null

    public fun deserialize(reader: Reader): T? = null

    public fun deserialize(content: String): T? = null

}

public fun <T: Any, U : Deserializable<T>> Request.response(deserializable: U, handler: (Request, Response, Result<T, FuelError>) -> Unit) {
    response(deserializable, { request, response, value ->
        handler(this@response, response, Result.Success(value))
    }, { request, response, error ->
        handler(this@response, response, Result.Failure(error))
    })
}

public fun <T: Any, U : Deserializable<T>> Request.response(deserializable: U, handler: Handler<T>) {
    response(deserializable, { request, response, value ->
        handler.success(request, response, value)
    }, { request, response, error ->
        handler.failure(request, response, error)
    })
}

private fun <T: Any, U : Deserializable<T>> Request.response(deserializable: U,
                                                        success: (Request, Response, T) -> Unit,
                                                        failure: (Request, Response, FuelError) -> Unit) {
    taskRequest.apply {
        successCallback = { response ->

            val deliverable: Result<T, Exception> =
                    try {
                        Result.of(deserializable.deserialize(response))
                    } catch(exception: Exception) {
                        Result.of(exception)
                    }

            callback {
                deliverable.fold({
                    success(this@response, response, it)
                }, {
                    failure(this@response, response, FuelError().apply { exception = it })
                })
            }
        }

        failureCallback = { error, response ->
            callback {
                failure(this@response, response, error)
            }
        }
    }

    submit(taskRequest)
}