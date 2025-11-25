# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SolaceQueueBrowserGui is a Java desktop GUI application for browsing and managing Solace message queues. It provides capabilities to view, filter, move, copy, and delete messages from Solace event broker queues. The tool addresses the limitation that standard Solace products do not provide a way to inspect message contents.

**Important**: This is NOT a Solace-supported product. It was created by Solace's professional services team.

## Working Directory

The main project is located at: `SolaceQueueBrowserGui/SolaceQueueBrowserGui/`

All Maven commands and development work should be executed from this directory.

## Build Commands

### Build the application
```bash
cd SolaceQueueBrowserGui/SolaceQueueBrowserGui
mvn clean package
```

This creates a fat JAR with all dependencies at:
`target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar`

### Compile only
```bash
cd SolaceQueueBrowserGui/SolaceQueueBrowserGui
mvn clean compile
```

### Clean build artifacts
```bash
cd SolaceQueueBrowserGui/SolaceQueueBrowserGui
mvn clean
```

## Running the Application

```bash
cd SolaceQueueBrowserGui/SolaceQueueBrowserGui
java -jar target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c=[path to config file]
```

Example:
```bash
java -jar target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c=config/sampleBrowserConfig.json
```

## Configuration

The application requires a JSON configuration file with broker connection details. See `config/sampleBrowserConfig.json` for the template.

Required configuration fields:
- **SEMP API credentials**: Admin credentials for pulling queue lists via SEMP REST API
- **Messaging credentials**: Client credentials for browsing queue messages via JCSMP
- **Broker URLs**: Both sempHost (SEMP API endpoint) and messagingHost (messaging endpoint)
- **Message VPN name**: The VPN context for operations

## Architecture

### High-Level Component Structure

The application follows a layered architecture:

1. **GUI Layer** (`com.solace.psg.queueBrowser.gui`)
   - `QueueBrowserMainWindow`: Main window displaying list of queues
   - `BrowserDialog`: Dialog for browsing messages within a selected queue
   - `FilterDialog`: UI for filtering messages by content/headers
   - Drag-and-drop support for moving/copying messages between queues

2. **Browser Layer** (`com.solace.psg.queueBrowser`)
   - `QueueBrowser`: Low-level wrapper around Solace JCSMP Browser API
   - `PaginatedCachingBrowser`: Higher-level browser with pagination and caching for GUI performance
   - Handles connection management, message fetching, and pagination logic

3. **Broker Integration Layer** (`com.solace.psg.brokers`)
   - `Broker`: Data model for broker connection properties
   - `SempClient`: Client for Solace SEMP v2 REST API (config, monitor, action endpoints)
   - Handles queue metadata operations (list queues, get queue stats, etc.)

4. **HTTP Layer** (`com.solace.psg.http`)
   - `HttpClient`: HTTP client wrapper for SEMP API calls
   - Custom authenticators and SSL trust managers for secure connections

### Key Architectural Patterns

- **Dual API Usage**: The application uses both SEMP REST API (for queue management) and JCSMP API (for message browsing). This is necessary because SEMP provides queue metadata while JCSMP provides message access.

- **Pagination and Caching**: `PaginatedCachingBrowser` wraps `QueueBrowser` to cache fetched messages in a LinkedHashMap, enabling efficient GUI pagination without repeated broker queries.

- **Factory Pattern**: `SempClient.SempClientFactory()` creates appropriate SEMP clients for config/monitor/action APIs by transforming the base URL.

- **Drag-and-Drop Architecture**: Implements `IDragDropInstigator` and `IDragDropTarget` interfaces with custom `TransferHandler` classes for moving/copying messages between queues via GUI drag-and-drop.

### Important Implementation Details

- **Java Version**: Requires Java 17 (configured in pom.xml)

- **Main Entry Point**: `com.solace.psg.queueBrowser.gui.QueueBrowserMainWindow`

- **Local Dependencies**: The project uses a local Maven repository (`lib/` directory) for Solace JARs. These are referenced in pom.xml via a file-based repository.

- **Logging**: Uses SLF4J with Log4j2 backend. Configuration in `config/log4j2.properties`. Logs go to `logs/browser.log` with 10MB rolling files (max 20 archives).

- **Queue Name Validation**: The `QueueBrowser.init()` method validates queue names before attempting connection to prevent empty/null queue errors.

- **Message Types Supported**: TextMessage, BytesMessage, MapMessage, StreamMessage, XMLContentMessage

## Common Development Tasks

When modifying browser behavior:
- Start with `BrowserDialog` for UI changes
- Modify `PaginatedCachingBrowser` for caching/pagination logic
- Touch `QueueBrowser` only for low-level JCSMP interactions

When adding SEMP API functionality:
- Extend `SempClient` with new API methods
- Use the existing HTTP client infrastructure
- Follow the pattern of existing methods (e.g., `getQueues()`)

When debugging connection issues:
- Check `config/log4j2.properties` to adjust log levels
- Logs contain detailed connection and operation traces
- JCSMP connection errors are wrapped in `BrokerException`

## Dependencies

Key external dependencies:
- Solace JCSMP API (10.21.0): Core messaging client
- Solace Messaging Client (1.4.0): Additional messaging capabilities
- OkHttp (4.12.0): HTTP client for SEMP API
- Gson (2.11.0): JSON serialization
- Commons CLI (1.9.0): Command-line argument parsing
- Log4j2 (2.24.1): Logging framework
- Swing: GUI framework (Java built-in)
