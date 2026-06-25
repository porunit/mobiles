package com.rmp.dbservice.error

class ConflictException(message: String) : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
