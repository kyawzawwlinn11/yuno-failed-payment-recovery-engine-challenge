package com.kyawzawwlinn.yuno.paymentrecovery.exception

import com.kyawzawwlinn.yuno.paymentrecovery.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = exception.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "invalid value"}" }
            .ifBlank { "Request validation failed" }

        return error(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
    )
    fun handleBadRequest(exception: Exception): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid request")

    @ExceptionHandler(NotFoundException::class, NoResourceFoundException::class)
    fun handleNotFound(exception: Exception): ResponseEntity<ErrorResponse> =
        error(HttpStatus.NOT_FOUND, exception.message ?: "Resource not found")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, exception.message ?: "Unexpected server error")

    private fun error(
        status: HttpStatus,
        message: String,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(message = message))
}
