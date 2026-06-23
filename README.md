# superkassa-offline-queue

[![Maven Central](https://img.shields.io/maven-central/v/io.github.texport/superkassa-offline-queue.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.texport/superkassa-offline-queue)
[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](#)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-offline-queue/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-offline-queue/actions)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

A lightweight, clean-architecture Kotlin/JVM library providing offline command queuing and synchronization mechanisms for the **Superkassa** fiscalization system.

It encapsulates queue management, retry policies with backoff, and distributed lease locking to ensure transaction safety and execution integrity in unstable network environments.

### Key Features
- **Offline Command Buffer**: Local queue buffer for commands waiting for internet connection, fully decoupled from database implementations via clean domain ports (`QueueStoragePort`).
- **Distributed Lease Locking**: Short-lived, owner-tied locks to prevent concurrent command processing across multiple server nodes or execution threads, defined via `LeaseLockPort`.
- **Smart Retries & Backoff**: Exponential or custom backoff policy configurations (`BackoffPolicy`) to schedule retries when processing fails due to network or service errors.
- **Port-based Clean Architecture**: Core services communicate entirely through clean interfaces, allowing seamless swapping of implementations (e.g., JDBC for servers, Room for Android).

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-offline-queue:1.0.1")
}
```

---

### Usage Example

```kotlin
import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.policy.DefaultBackoffPolicy
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.application.service.QueueService
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus

// 1. Define command handler
val commandHandler = object : QueueCommandHandler {
    override fun handle(command: QueueCommand): DispatchResult {
        println("Processing command: ${command.id}")
        return DispatchResult(status = QueueStatus.SENT)
    }
}

// 2. Initialize Service with your storage and lock implementations
val queueService = QueueService(
    storage = myStoragePortImpl,
    lockPort = myLeaseLockPortImpl,
    handler = commandHandler,
    backoffPolicy = DefaultBackoffPolicy(),
    ownerId = "node-1"
)

// 3. Process commands
queueService.processBatch(cashboxId = "cashbox-123", lane = QueueLane.OFFLINE, limit = 10)
```

---

## Документация на русском языке

Легковесная библиотека на Kotlin/JVM для управления локальной очередью команд и асинхронной синхронизации в системе фискализации **Superkassa**.

Она инкапсулирует логику буферизации, политики повторных попыток с экспоненциальной задержкой (backoff) и распределенную аренду блокировок (lease locking) для обеспечения транзакционной безопасности в нестабильных сетевых условиях.

### Ключевые возможности
- **Автономный буфер команд**: Локальное накопление команд в очереди в режиме офлайн, полностью абстрагированное от реализации хранилища через порт `QueueStoragePort`.
- **Распределенные блокировки (Lease Locking)**: Защита от параллельного запуска обработки очереди между несколькими серверами (нодами) или фоновыми процессами с ограничением по времени и привязкой к конкретному владельцу (`LeaseLockPort`).
- **Интеллектуальный перезапуск**: Гибкая настройка задержек между повторными попытками отправки чеков в ОФД при сбоях связи с использованием `BackoffPolicy`.
- **Чистая архитектура**: Логика сервиса полностью развязана с технологиями хранения, что позволяет использовать общие алгоритмы очереди как на сервере (через JDBC), так и на мобильных устройствах Android (через Room).

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-offline-queue:1.0.1")
}
```

---

### Пример использования

```kotlin
import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.policy.DefaultBackoffPolicy
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.application.service.QueueService
import kz.mybrain.superkassa.offline_queue.domain.model.QueueCommand
import kz.mybrain.superkassa.offline_queue.domain.model.QueueLane
import kz.mybrain.superkassa.offline_queue.domain.model.QueueStatus

// 1. Создание обработчика команд
val commandHandler = object : QueueCommandHandler {
    override fun handle(command: QueueCommand): DispatchResult {
        println("Отправка команды: ${command.id}")
        return DispatchResult(status = QueueStatus.SENT)
    }
}

// 2. Инициализация сервиса с реализацией БД и блокировок
val queueService = QueueService(
    storage = myStoragePortImpl,
    lockPort = myLeaseLockPortImpl,
    handler = commandHandler,
    backoffPolicy = DefaultBackoffPolicy(),
    ownerId = "node-1"
)

// 3. Запуск обработки пакета команд
queueService.processBatch(cashboxId = "cashbox-123", lane = QueueLane.OFFLINE, limit = 10)
```
