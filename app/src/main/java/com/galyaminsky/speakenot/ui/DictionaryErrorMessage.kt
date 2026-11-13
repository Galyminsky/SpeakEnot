package com.galyaminsky.speakenot.ui

import com.galyaminsky.speakenot.domain.DictionaryError

fun dictionaryErrorMessage(error: DictionaryError): String = when (error) {
    DictionaryError.Offline -> "Нет подключения к интернету. Повторите запрос вручную."
    DictionaryError.Network -> "Сетевая ошибка. Проверьте подключение и повторите запрос."
    DictionaryError.Timeout -> "Время ожидания истекло. Повторите запрос."
    DictionaryError.EmptyResponse -> "Сервис вернул пустой ответ."
    is DictionaryError.Http -> "Ошибка сервиса: ${error.code}."
    DictionaryError.Unknown -> "Не удалось выполнить запрос."
}
