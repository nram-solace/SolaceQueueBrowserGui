![Project Logo](./SolaceQueueBrowserGui/docs/img/logo.png "SolaceQueueBrowserGui 2.0 Logo")

# SolaceQueueBrowserGui 2.0

Desktop GUI for browsing Solace Queues and managing messages.

## Overview

SolaceQueueBrowserGui 2.0 is a desktop application that provides a comprehensive interface for browsing, inspecting, and managing messages in Solace queues. 

### Key Features

- **Multi-broker support** - Connect to and switch between multiple Solace brokers dynamically
- **Queue management** - View, search, filter, and sort queues with real-time updates
  - Search queues by name (case-insensitive)
  - Filter by queue type (Exclusive, Non-Exclusive, Partitioned, Last Value Queue)
  - Filter by category (User, System, All)
  - Sort by name, spool size, spool usage, or usage percentage
- **Message browsing** - Paginated message browser with detailed inspection
  - View message headers, user properties, and payload
  - Payload format selection (Plain, JSON, YAML, CSV)
  - Text wrapping for long payloads
  - Page navigation with configurable page size
- **Message operations** - Comprehensive message management
  - **Move** - Move messages between queues (removes from source)
  - **Copy** - Copy messages to another queue (keeps in source)
  - **Delete** - Delete messages from queues
  - **Download** - Download messages to ZIP files for offline analysis
  - **Restore** - Restore previously downloaded messages back to queues
  - Bulk operations support (select multiple messages)
- **Filtering** - Filter messages by multiple criteria
  - Filter by message headers (Destination, TTL, Delivery Mode, etc.)
  - Filter by user properties
  - Filter by payload content (contains/does not contain)
  - Combine multiple filter conditions
- **Password encryption** - Secure password storage with AES-256-GCM encryption
  - Encrypt passwords in configuration files
  - Master password prompt or command-line option
  - Backward compatible with plain text passwords
- **Cross-platform** - Runs on Windows, macOS, Linux, and WSL

## Quick Start

### Prerequisites

- Java Runtime Environment (JRE) 17 or higher
- Network access to Solace broker SEMP API endpoint
- Network access to Solace broker messaging endpoint
- Appropriate credentials (SEMP admin and messaging client)

### Running the Application

1. **Extract the distribution package** (if you haven't already):
   ```bash
   unzip SolaceQueueBrowserGui-VERSION-runtime-distribution.zip
   cd SolaceQueueBrowserGui-VERSION/
   ```

2. **Create your broker configuration file**:
   - Copy `config/sample-config.json` to `config/default.json`
   - Edit `config/default.json` with your specific broker connection details
   - See `config/sample-config.json` for reference structure

3. **Run the application**:
   ```bash
   ./scripts/run.sh -c config/default.json
   ```

For detailed instructions, see the [User Guide](./docs/USER_GUIDE.md).

## Configuration

The application uses JSON configuration files to connect to Solace brokers. A sample configuration file is provided at `config/sample-config.json` for reference.

**To get started:**
1. Copy `config/sample-config.json` to `config/default.json`
2. Edit `config/default.json` with your specific broker connection details (hostnames, credentials, VPN names, etc.)

**Basic configuration structure:**
```json
{
  "eventBrokers": [
    {
      "name": "My Broker",
      "sempHost": "https://broker.example.com:943/SEMP/v2/config",
      "sempAdminUser": "admin",
      "sempAdminPw": "password",
      "msgVpnName": "default",
      "messagingHost": "tcps://broker.example.com:55443",
      "messagingClientUsername": "client",
      "messagingPw": "password"
    }
  ]
}
```

**Note:** The application requires both SEMP admin credentials (for queue management) and messaging client credentials (for message browsing).

For detailed configuration instructions, see the [User Guide](./docs/USER_GUIDE.md).

## Password Encryption

The application supports encrypted passwords for secure configuration file storage. Use the `crypt-util.sh` script to encrypt passwords:

```bash
./scripts/crypt-util.sh encrypt
```

Encrypted passwords use the format: `ENC:AES256GCM:...`

When encrypted passwords are detected, the application will prompt for a master password (or use `--master-password` command-line option).

For more information, see the [Password Encryption section](./docs/USER_GUIDE.md#password-encryption) in the User Guide.

## Message Operations

### Browse Messages
Select a queue and click "Browse" to open the message browser. Messages are displayed in a paginated table with detailed information.

### Move, Copy, and Delete
- **Move**: Transfers messages from source queue to target queue (removes from source)
- **Copy**: Duplicates messages to target queue (keeps in source)
- **Delete**: Permanently removes messages from queue

Operations can be performed on single messages or multiple selected messages.

### Download Messages
Download selected messages to ZIP files for offline analysis:
- Messages saved to `./downloads/{hostname}/{vpn-name}/{queue-name}/`
- Each message saved as individual ZIP file with payload, headers, and user properties
- Files organized by broker, VPN, and queue for easy management

### Restore Messages
Restore previously downloaded messages back to queues:
- Select target queue and click "Restore"
- Browse to directory containing downloaded message ZIP files
- Select messages to restore (supports bulk selection)
- Messages are republished with original headers and properties preserved
- Can restore to different queues/VPNs/hosts with confirmation

For detailed instructions, see the [Operations section](./docs/USER_GUIDE.md#operations) in the User Guide.

## Features in Detail

### Queue Management
- **Real-time search** - Filter queues by name as you type
- **Queue type filters** - Show Exclusive, Non-Exclusive, Partitioned, or Last Value Queues
- **Category filters** - Display User queues, System queues, or All queues
- **Sorting** - Sort by name, spool size, spool usage, or usage percentage (ascending/descending)
- **Queue details** - View detailed information for selected queue (message count, spool usage, access type)

### Message Browser
- **Pagination** - Navigate through messages with Previous/Next page controls
- **Configurable page size** - Adjust number of messages per page (default: 20)
- **Message selection** - Select individual messages or use select-all checkbox
- **Message details** - View headers, user properties, and payload in separate panels
- **Payload formatting** - Format payload as Plain, JSON, YAML, or CSV
- **Text wrapping** - Toggle text wrapping for long payloads

### Message Filtering
- **Header filters** - Filter by Destination, TTL, Delivery Mode, Correlation ID, etc.
- **User property filters** - Filter by custom user-defined properties
- **Payload filters** - Search payload content (contains/does not contain)
- **Combined filters** - Apply multiple filter conditions simultaneously
- **Filter status** - Clear indication when filters are active

### Bulk Operations
- Select multiple messages using checkboxes
- Perform operations on all selected messages at once
- Confirmation dialogs show operation results
- Automatic selection management after operations

## Package Contents

- **Application JAR** - Self-contained executable with all dependencies
- **Configuration files** - System config, logging config, sample config templates (`sample-config.json`, `solace-cloud.json`), and icons
- **Runtime scripts** - `run.sh` (application launcher) and `crypt-util.sh` (password encryption utility)
- **Documentation** - This README and comprehensive User Guide in `docs/` folder

**Note:** Create your own `config/default.json` by copying `config/sample-config.json` and updating it with your broker connection details.

## Documentation

- **[User Guide](./docs/USER_GUIDE.md)** - Complete user guide and reference manual with detailed instructions, configuration examples, troubleshooting, and more

## Important Disclaimer

**This tool is NOT a Solace supported product.** It has been created by Solace's professional services team to augment Solace products.

## Feedback

Send feature requests, defects and comments to ramesh.natarajan@solace.com & mike.obrien@solace.com
