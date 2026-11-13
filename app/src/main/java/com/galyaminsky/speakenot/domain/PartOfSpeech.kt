package com.galyaminsky.speakenot.domain

fun partOfSpeechLabel(code: String?): String = when (code) {
    "n" -> "Существительное"
    "v" -> "Глагол"
    "j" -> "Прилагательное"
    "r" -> "Наречие"
    "prp" -> "Предлог"
    "prn" -> "Местоимение"
    "crd" -> "Числительное"
    "cjc" -> "Союз"
    "exc" -> "Междометие"
    "det" -> "Артикль"
    "abb" -> "Сокращение"
    "x" -> "Частица"
    "ord" -> "Порядковое числительное"
    "md" -> "Модальный глагол"
    "ph" -> "Фраза"
    "phi" -> "Идиома"
    null -> "Часть речи не указана"
    else -> code
}
