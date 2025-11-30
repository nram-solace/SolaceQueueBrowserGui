# SQMB+ User Guide and Reference Manual

## Table of Contents

1. [Introduction](#introduction)
2. [Installation and Setup](#installation-and-setup)
3. [Configuration](#configuration)
4. [Main Window](#main-window)
5. [Message Browser](#message-browser)
6. [Operations](#operations)
7. [Filtering and Sorting](#filtering-and-sorting)
8. [Password Encryption](#password-encryption)
9. [Troubleshooting](#troubleshooting)
10. [Reference](#reference)

---

## Introduction

SQMB+ (Solace Queue Message Browser & More) is a desktop GUI application for browsing, inspecting, and managing messages in Solace queues. The application provides a comprehensive interface for message operations including viewing, filtering, moving, copying, deleting, and downloading messages.

### Key Features

- Multi-broker support with dynamic switching
- Queue filtering and sorting capabilities
- Message filtering by headers, properties, and payload content
- Bulk message operations (move, copy, delete)
- Message download functionality
- Password encryption support
- Cross-platform compatibility (Windows, macOS, Linux, WSL)

### System Requirements

- Java Runtime Environment (JRE) 8 or higher
- Network access to Solace broker SEMP API endpoint
- Network access to Solace broker messaging endpoint
- Appropriate credentials (SEMP admin and messaging client)

---

## Installation and Setup

### Building from Source

The application can be built using Maven:

```bash
mvn clean package
```

This creates a self-contained executable JAR file in the `target/` directory: `SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar`

### Running the Application

#### Using the Run Script

```bash
./scripts/run.sh -c config/default.json
```

#### Using Java Directly

```bash
java -jar target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c config/default.json
```

#### Command-Line Options

- `-c, --config FILE`: Specify configuration file path (required)
- `-mp, --master-password PWD`: Provide master password for encrypted passwords
- `-h, --help`: Display help information

**Screenshot Placeholder: Command-line help output**

---

## Configuration

### Configuration File Structure

The application uses a two-file configuration system:
1. **System Configuration** (`config/system.json`) - Contains system and internal properties
2. **User Configuration** - Contains user configs such as Solace broker access info.

The system configuration is loaded first, followed by the user configuration file. 

#### System Configuration File (`config/system.json`)

**REQUIRED**: The system configuration file is mandatory. The application will exit with an error if this file doesn't exist or fails to load/parse.

**WARNING**: DONOT modify this file unless you are sure what you are doing. 

```json
{
  "version": "v2.2.0",
  "downloadFolder": "./downloads",
  "ui": {
    "fontFamily": null,
    "defaultFontSize": 14,
    "headerFontSize": 16,
    "statusFontSize": 22
  }
}
```

**Error Handling:**
- If `config/system.json` doesn't exist, the application exits with: "Failed to read system configuration file 'config/system.json': [error]. The system.json file is required."
- If the file contains invalid JSON, the application exits with: "Failed to parse system configuration file 'config/system.json': [error]. The system.json file must be valid JSON."
- If processing the file fails, the application exits with: "Failed to process system configuration file 'config/system.json': [error]"



#### User Configuration File Format

User configuration files contain **only** broker connection information. The `eventBrokers` array format is required - the legacy single `eventBroker` object format is no longer supported.

**REQUIRED FORMAT**: User configuration files must use the `eventBrokers` array format:

```json
{
  "eventBrokers": [
    {
      "name": "Broker Display Name",
      "sempHost": "http://localhost:8080/SEMP/v2/config",
      "sempAdminUser": "admin",
      "sempAdminPw": "password",
      "msgVpnName": "default",
      "messagingHost": "tcp://localhost:55333",
      "messagingClientUsername": "default",
      "messagingPw": "password"
    }
  ]
}
```

**Error Handling:**
- If the user config file doesn't exist or can't be read, the application exits with: "Failed to read user configuration file: [error]"
- If the file contains invalid JSON, the application exits with: "Failed to parse user configuration file: [error]"
- If the file doesn't contain `eventBrokers` array, the application exits with: "User configuration must contain 'eventBrokers' array"
- If processing the configuration fails, the application exits with: "Failed to process user configuration: [error]"
- If processing `eventBrokers` fails, the application exits with: "Failed to process eventBrokers configuration: [error]"

**Legacy Format No Longer Supported:**
- The old `eventBroker` (singular) object format is no longer supported
- Users must migrate to the `eventBrokers` array format
- Single broker configurations should wrap the broker object in an array: `"eventBrokers": [{...}]`

**Broker Configuration (`eventBrokers` array):**

- `name`: Display name for the broker (shown in broker selector dropdown)
- `sempHost`: SEMP v2 API endpoint URL (supports http://, https://)
- `sempAdminUser`: SEMP API administrator username
- `sempAdminPw`: SEMP API administrator password (supports encrypted format)
- `msgVpnName`: Message VPN name
- `messagingHost`: Messaging endpoint URL (supports tcp://, tcps://)
- `messagingClientUsername`: Messaging client username
- `messagingPw`: Messaging client password (supports encrypted format)


**Screenshot Placeholder: Configuration file example with multiple brokers**


---

## Main Window

The main window provides the primary interface for queue selection and message operations.

**Screenshot Placeholder: Main window overview**

### Window Components

#### Header Panel

The header panel displays broker connection information and controls:

- **Broker Selector**: Dropdown menu (visible when multiple brokers configured) for switching between configured brokers
- **Connection Information**: Displays current broker hostname, VPN name, SEMP user, and messaging client user
- **Application Icon**: SQMB+ logo

**Screenshot Placeholder: Header panel with broker selector**

#### Queue Filter and Sort Panel

Located below the header, this panel provides queue filtering and sorting capabilities:

- **Search Field**: Real-time text search for queue names (case-insensitive substring matching)
- **Queue Type Filters**: Checkboxes for Exclusive, Non-Exclusive, Partitioned, Last Value Queue
- **Category Filters**: Radio buttons for All, User (default), System queues
- **Sort Options**: Dropdown for sort criteria (Name, Spool Size, Spool Usage, Spool Usage %)
- **Sort Direction**: Button to toggle ascending/descending order
- **Queue Count**: Label showing "Showing X of Y queues"

**Screenshot Placeholder: Filter and sort panel**

#### Queue Table

Displays filtered and sorted queues with the following columns:

- **Queues**: Queue name
- **Messages**: Current message count
- **Spool Size**: Allocated spool size (maxMsgSpoolUsage)
- **Spool Usage**: Current spool usage (msgSpoolUsage)
- **Spool Usage %**: Percentage of spool used

**Screenshot Placeholder: Queue table with selected queue**

#### Queue Details Panel

Located on the right side, displays detailed information for the selected queue:

- Queue name
- Message count
- Spool usage details
- Access type
- Partition information (if applicable)

**Screenshot Placeholder: Queue details panel**

#### Action Buttons

Located at the bottom of the window:

- **Browse**: Open message browser for selected queue
- **Copy ALL**: Copy all messages from selected queue to target queue
- **Move ALL**: Move all messages from selected queue to target queue
- **Delete ALL**: Delete all messages from selected queue
- **Restore**: Restore messages from downloaded ZIP files to selected queue
- **Refresh**: Refresh queue list and details
- **Exit**: Close application

**Screenshot Placeholder: Action buttons**

### Queue Selection

- **Single Click**: Selects a queue and displays details in the details panel
- **Double Click**: Opens the message browser for the selected queue
- **Keyboard Navigation**: Arrow keys navigate through the queue list

### Broker Switching

When multiple brokers are configured:

1. Select broker from dropdown in header panel
2. Application automatically reconnects to selected broker
3. Queue list refreshes with queues from new broker
4. Connection information updates to reflect new broker

**Screenshot Placeholder: Broker switching dialog**

---

## Message Browser

The message browser window opens when browsing a queue. It provides detailed message inspection and management capabilities.

**Screenshot Placeholder: Message browser window overview**

### Window Components

#### Top Control Panel

- **Queue Name**: Displays current queue being browsed
- **Page Navigation**: Previous Page and Next Page buttons
- **Page Information**: Shows current page number and total pages (e.g., "Page 1 of ~ 10")
- **Page Size**: Dropdown to select messages per page (default: 20)
- **Filter Status**: Shows filter status as "(ON)" or "(OFF)"
- **Filter Button**: Opens filter dialog
- **Refresh Button**: Reloads current page

**Screenshot Placeholder: Top control panel**

#### Message Table

Displays messages for the current page with columns:

- **Checkbox**: Select individual messages for bulk operations
- **Message ID**: Unique message identifier
- **Timestamp**: Message timestamp
- **Destination**: Message destination topic or queue
- **Size**: Message size in bytes

**Screenshot Placeholder: Message table with selected messages**

#### Select-All Checkbox

Located in the top-left corner of the message table (intersection of row and column headers):

- **Checked**: All messages on current page are selected
- **Unchecked**: No messages are selected
- Automatically updates based on individual checkbox selections

**Screenshot Placeholder: Select-all checkbox location**

#### Message Details Panels

Located below the message table, split into three sections:

**Headers Panel:**
- Displays message header fields (Destination, Delivery Mode, TTL, etc.)
- Read-only display of message metadata

**User Properties Panel:**
- Displays user-defined properties
- Key-value pairs in table format

**Payload Panel:**
- Displays message payload content
- Format selection dropdown: Plain, JSON, YAML, CSV
- Wrap text checkbox for long lines
- Monospaced font for better readability

**Screenshot Placeholder: Message details panels**

### Message Selection

- **Individual Selection**: Click checkbox in first column
- **Select All**: Click select-all checkbox in top-left corner or click column header
- **Keyboard Navigation**: Arrow keys navigate through messages
- **Row Selection**: Clicking a row selects the message and displays details

### Page Navigation

- **Previous Page**: Navigate to previous page of messages
- **Next Page**: Navigate to next page of messages
- **Page Size**: Change number of messages per page (dropdown in top panel)
- **Page Count**: Displays estimated total pages based on message count and page size

The Next button is automatically disabled when:
- No more messages are available (unfiltered view)
- All filtered messages have been displayed (filtered view)
- Current page has fewer messages than page size (indicates end of results)

---

## Operations

### Browse Messages

**Purpose**: Open message browser to inspect messages in a queue.

**Steps**:
1. Select a queue from the queue table
2. Click "Browse" button or double-click the queue name
3. Message browser window opens displaying first page of messages

**Screenshot Placeholder: Browse operation**

### Move Messages

**Purpose**: Move selected messages from current queue to another queue.

**Single Message**:
1. Select message in message table
2. Select target queue from dropdown
3. Click "Move" button
4. Confirm operation in dialog

**Multiple Messages**:
1. Select messages using checkboxes
2. Select target queue from dropdown
3. Click "Move" button
4. Confirm operation in dialog

**Bulk Operation (All Messages)**:
1. In main window, select source queue
2. Select target queue from dropdown
3. Click "Move ALL" button
4. Confirm operation in dialog

**Screenshot Placeholder: Move operation dialog**

**Behavior**:
- Messages are removed from source queue
- Messages are added to target queue
- Confirmation dialog shows number of messages moved
- UI updates immediately after successful operation

### Copy Messages

**Purpose**: Copy selected messages from current queue to another queue (messages remain in source queue).

**Single Message**:
1. Select message in message table
2. Select target queue from dropdown
3. Click "Copy" button
4. Confirm operation in dialog

**Multiple Messages**:
1. Select messages using checkboxes
2. Select target queue from dropdown
3. Click "Copy" button
4. Confirm operation in dialog

**Bulk Operation (All Messages)**:
1. In main window, select source queue
2. Select target queue from dropdown
3. Click "Copy ALL" button
4. Confirm operation in dialog

**Screenshot Placeholder: Copy operation dialog**

**Behavior**:
- Messages are copied to target queue
- Messages remain in source queue
- Confirmation dialog shows number of messages copied
- Selected messages are automatically unselected after operation

### Delete Messages

**Purpose**: Permanently delete selected messages from a queue.

**Single Message**:
1. Select message in message table
2. Click "Delete" button
3. Confirm deletion in dialog

**Multiple Messages**:
1. Select messages using checkboxes
2. Click "Delete" button
3. Confirm deletion in dialog (shows count: "Are you sure you want to delete N message(s)?")

**Bulk Operation (All Messages)**:
1. In main window, select queue
2. Click "Delete ALL" button
3. Confirm deletion in dialog

**Screenshot Placeholder: Delete confirmation dialog**

**Behavior**:
- Messages are permanently removed from queue
- Confirmation dialog shows number of messages deleted
- UI updates immediately after successful operation
- Page count recalculates after deletion

**No Selection Handling**:
- If no checkboxes are selected, the currently focused row is used
- If no row is focused, operation is cancelled with status message

### Download Messages

**Purpose**: Download selected messages to a ZIP file for offline analysis.

**Steps**:
1. Select one or more messages using checkboxes
2. Click "Download" button
3. Messages are saved to ZIP file in configured download folder
4. Confirmation dialog shows download location and count

**Screenshot Placeholder: Download operation**

**File Format**:
- ZIP file path: `./downloads/{hostname}/{vpn-name}/{queue-name}/msg-{MessageID}-{YYYYMMDDHHSS}.zip`
- Directory structure organized by broker hostname, VPN name, and queue name
- Queue names and VPN names sanitized (replaces "/" and spaces with "_")
- Timestamp format: `YYYYMMDDHHSS` (e.g., `20240115143025`)
- Each message saved as individual ZIP file containing payload, headers, and user properties
- Original message format preserved

**Behavior**:
- Messages remain in source queue (not removed)
- Selected messages are automatically unselected after download
- Directory structure created automatically if it doesn't exist
- Download path shown in confirmation dialog

### Restore Messages

**Purpose**: Restore previously downloaded messages from ZIP files back to a Solace queue.

**Steps**:
1. Select a queue in the main window (this will be the target queue for restoration)
2. Click "Restore" button
3. Restore dialog opens
4. Click "Select Directory" button
5. Navigate to directory containing downloaded message ZIP files (typically `./downloads/{hostname}/{vpn-name}/{queue-name}/`)
6. Select directory and click "Open"
7. Messages from the directory are displayed in a paginated table
8. Select messages to restore using checkboxes (or use select-all checkbox in header)
9. Click "Restore" button
10. If source and target differ (different broker/VPN/queue), confirm the mismatch in dialog
11. Messages are restored to the target queue
12. Confirmation dialog shows number of messages restored

**Screenshot Placeholder: Restore dialog**

**File Format**:
- Restore dialog reads from configured `downloadFolder` (default: `./downloads`)
- Expected directory structure: `{downloadFolder}/{hostname}/{vpn-name}/{queue-name}/`
- ZIP file naming: `msg-{MessageID}-{YYYYMMDDHHSS}.zip`
- Each ZIP file contains: `payload.txt`, `headers.txt`, `userProps.txt`

**Message Selection**:
- Messages displayed in paginated table (20 per page by default)
- Select individual messages using checkboxes
- Select all messages using checkbox in table header
- Selection persists across pages
- Messages identified by composite key (messageId + timestamp) to handle duplicates

**Source/Target Validation**:
- System extracts source broker/VPN/queue from directory path
- Compares with target queue (selected in main window)
- Prompts user if source and target differ (different broker, VPN, or queue)
- User can proceed or cancel the restore operation

**Behavior**:
- Messages are published to target queue using JCSMP API
- Message headers and user properties are preserved
- Original message format is maintained
- Selected messages are automatically unselected after successful restore
- Progress shown during restore operation
- Confirmation dialog shows success/failure counts

**Restrictions**:
- Restore button only enabled when a queue is selected in main window
- Directory must contain valid message ZIP files
- ZIP files must follow expected format and structure
- Target queue must exist and be accessible

---

## Filtering and Sorting

### Queue Filtering

#### Search Filter

- **Location**: Search field in filter panel
- **Function**: Case-insensitive substring matching on queue names
- **Behavior**: Updates in real-time as you type
- **Combines With**: All other filters (AND logic)

**Screenshot Placeholder: Search filter**

#### Queue Type Filters

- **Exclusive**: Queues with `accessType = "exclusive"`
- **Non-Exclusive**: Queues with `accessType = "non-exclusive"`
- **Partitioned**: Queues with `partitionCount > 0`
- **Last Value Queue**: Queues with `maxMsgSpoolUsage == 0`

**Behavior**:
- Multiple types can be selected (OR logic)
- Queues matching any selected type are shown
- Works in combination with search and category filters

**Screenshot Placeholder: Queue type filters**

#### Category Filters

- **All**: Shows all queues (user and system)
- **User**: Shows only user queues (names not starting with "#")
- **System**: Shows only system queues (names starting with "#")

**Default**: User queues (system queues hidden by default)

**Screenshot Placeholder: Category filters**

### Queue Sorting

#### Sort Options

- **Name**: Alphabetical order (case-insensitive)
- **Spool Size**: By allocated spool size (`maxMsgSpoolUsage`)
- **Spool Usage**: By current spool usage (`msgSpoolUsage`)
- **Spool Usage %**: By usage percentage (`msgSpoolUsage / maxMsgSpoolUsage`)

#### Sort Direction

- Toggle button switches between ascending and descending
- Visual indicator shows current direction (↑ or ↓)

**Screenshot Placeholder: Sort controls**

### Message Filtering

#### Filter Dialog

Opens when clicking "Filter" button in message browser.

**Screenshot Placeholder: Filter dialog**

#### Filter Criteria

**Header Filter**:
- **Field**: Select header field (Destination, Delivery Mode, TTL, etc.)
- **Condition**: Equals, Contains, Does not contain
- **Value**: Filter value (text, number, or picklist selection)

**User Property Filter**:
- **Property Name**: User-defined property name
- **Condition**: Equals, Contains, Does not contain
- **Value**: Property value to match

**Payload Filter**:
- **Condition**: Contains, Does not contain
- **Value**: Text to search in message payload

**Screenshot Placeholder: Filter criteria fields**

#### Filter Behavior

- All criteria are combined with AND logic
- Filter applies to all pages (not just current page)
- Page count adjusts when filter is active
- Filter status shown as "(ON)" or "(OFF)" in top panel
- Clearing filter resets to show all messages

#### Clearing Filters

- Click "Filter" button to open dialog
- Set all conditions to "-no filter-"
- Click "Apply" to clear filters

**Screenshot Placeholder: Clearing filters**

---

## Password Encryption

### Overview

The application supports AES-256-GCM encryption for passwords stored in configuration files. Encrypted passwords can be safely stored in version control or shared configuration files.

### Encryption Format

Encrypted passwords use the format:
```
ENC:AES256GCM:{base64_encrypted_data}:{base64_iv}:{base64_salt}
```

### Encrypting Passwords

#### Using the CLI Tool

**Interactive Mode**:
```bash
java -cp SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar \
  com.solace.psg.util.PasswordEncryptionCLI encrypt
```

**Non-Interactive Mode**:
```bash
java -cp SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar \
  com.solace.psg.util.PasswordEncryptionCLI encrypt "password" "masterKey"
```

#### Using the Shell Script

**Interactive Mode**:
```bash
./scripts/encrypt-password.sh encrypt
```

**Non-Interactive Mode**:
```bash
./scripts/encrypt-password.sh encrypt "password" "masterKey"
```

**Screenshot Placeholder: Password encryption CLI**

### Updating Configuration File

Replace plain text passwords with encrypted values:

```json
{
  "eventBrokers": [{
    "sempAdminPw": "ENC:AES256GCM:U2FsdGVkX1+...",
    "messagingPw": "ENC:AES256GCM:glqBL0OCe+13V/62Zx3is7vNhtQ3ug0=..."
  }]
}
```

### Running with Encrypted Passwords

#### GUI Prompt (Recommended)

When the application detects encrypted passwords, it prompts for the master password:

1. Launch application
2. Master password dialog appears
3. Enter master password
4. Application decrypts passwords and connects

**Screenshot Placeholder: Master password prompt**

#### Command-Line Master Password

```bash
./scripts/run.sh -c config/default.json --master-password "masterKey"
```

**Note**: Quote the master password if it contains special characters (e.g., `#`, `$`, `!`).

### Decrypting Passwords (Verification)

To verify an encrypted password:

```bash
./scripts/encrypt-password.sh decrypt "ENC:AES256GCM:..." "masterKey"
```

### Security Considerations

- Master password is never stored in configuration files
- Master password must be provided at runtime
- Use strong master passwords
- Encrypted passwords can be safely stored in version control
- Each encryption uses unique salt and IV for security

---

## Troubleshooting

### Connection Issues

#### SEMP Connection Failures

**Solutions**:
- Verify SEMP host URL is correct and accessible
- Check SEMP admin credentials
- Verify network connectivity to broker
- Check firewall rules for SEMP port (typically 8080 for HTTP, 943 for HTTPS)

**Screenshot Placeholder: SEMP connection error**
#### SMF (Messaging) Connection Failures

**Symptoms**:
- Message browser opens but shows no messages
- Error dialog shows SMF connection failure

**Solutions**:
- Verify messaging host URL is correct
- Check messaging client credentials
- Verify VPN name matches configuration
- Check network connectivity to messaging endpoint
- Verify firewall rules for messaging port (typically 55555 for TCP, 55443 for TCPS)

### Password Decryption Errors

**Symptoms**:
- Error dialog: "Failed to decrypt password" or "Wrong master password"

**Solutions**:
- Verify master password is correct
- Ensure master password matches the one used for encryption
- Check for special characters that may need quoting in command line
- Use GUI prompt instead of command-line password if issues persist

### Message Loading Issues

**Symptoms**:
- Messages do not appear in browser
- Empty message table

**Solutions**:
- Check queue has messages (verify in main window)
- Verify SMF connection is successful
- Check filter settings (may be filtering out all messages)
- Try refreshing the page
- Check application logs in `logs/browser.log`

### UI Display Issues

**Symptoms**:
- Text clipped or not displaying
- Fonts not rendering correctly

**Solutions**:
- Check UI configuration in config file
- Verify font family is available on your system
- Set `fontFamily` to `null` to use system defaults
- Check log files for font-related errors


### Performance Issues

**Symptoms**:
- Slow queue list loading
- Slow message browsing

**Solutions**:
- Reduce page size for message browsing
- Use queue filters to limit displayed queues
- Check network latency to broker
- For large queue counts, filtering helps reduce UI load

---

## Reference

### Configuration File Schema

#### System Configuration Schema (`config/system.json`)

```json
{
  "version": "string (optional, default: v2.1.3)",
  "downloadFolder": "string (optional, default: ./downloads)",
  "ui": {
    "fontFamily": "string | null (optional, null uses system default)",
    "defaultFontSize": "integer (optional, default: 14)",
    "headerFontSize": "integer (optional, default: 16)",
    "statusFontSize": "integer (optional, default: 22)"
  }
}
```

**Note:** The system configuration file is **required**. The application will exit with an error if `config/system.json` doesn't exist or fails to load/parse.

#### User Configuration Schema

```json
{
  "eventBrokers": [
    {
      "name": "string (required)",
      "sempHost": "string (required, URL)",
      "sempAdminUser": "string (required)",
      "sempAdminPw": "string (required, plain text or ENC:...)",
      "msgVpnName": "string (required)",
      "messagingHost": "string (required, URL)",
      "messagingClientUsername": "string (required)",
      "messagingPw": "string (required, plain text or ENC:...)"
    }
  ]
}
```

**Note:** User configuration files should contain only broker information. System properties (`downloadFolder`, `ui`, `version`) can still be included for backward compatibility, but are not recommended. System properties should be maintained in `config/system.json`.

**Important:** The `eventBrokers` array format is required. The legacy `eventBroker` (singular) object format is no longer supported. If your configuration uses the old format, you must migrate to the array format.

### Supported URL Formats

#### SEMP Host URLs

- `http://host:port/SEMP/v2/config`
- `https://host:port/SEMP/v2/config`
- `http://host/SEMP/v2/config` (default port 80)
- `https://host/SEMP/v2/config` (default port 443)

#### Messaging Host URLs

- `tcp://host:port`
- `tcps://host:port`
- `tcp://host` (default port 55555)
- `tcps://host` (default port 55443)

### Message Header Fields

The following header fields are available for filtering:

- Destination
- Delivery Mode (DIRECT, PERSISTENT, NON_PERSISTENT)
- Reply-To Destination
- Time-To-Live (TTL) - numeric
- DMQ Eligible - boolean
- Immediate Acknowledgement - boolean
- Redelivery Flag - boolean
- Deliver-To-One - boolean
- Class of Service (CoS) - 0-9
- Eliding Eligible - boolean
- Message ID
- Correlation ID
- Message Type (Message, Text, Binary, Stream, Map, Object)
- Encoding

### Filter Conditions

- **-no filter-**: No filtering on this field
- **equals**: Exact match
- **contains**: Substring match
- **does not contain**: Exclude substring match

### Keyboard Shortcuts

#### Main Window

- **Arrow Keys**: Navigate queue list
- **Enter**: Browse selected queue
- **F5**: Refresh queue list

#### Message Browser

- **Arrow Keys**: Navigate messages
- **Space**: Toggle checkbox for selected message
- **Ctrl+A**: Select all messages (if supported)
- **F5**: Refresh current page

### Log Files

Application logs are written to:

- **Browser Log**: `logs/browser.log`
- **Command Log**: `logs/command.log`

Log levels and formatting configured in `config/log4j2.properties`.

### Error Messages

#### Common Error Messages

- **"SMF (Messaging) connection failed"**: Cannot connect to messaging endpoint
- **"SEMP connection failed"**: Cannot connect to SEMP API endpoint
- **"Failed to decrypt password"**: Master password incorrect or encryption format invalid
- **"Master password is required"**: Encrypted passwords detected but no master password provided
- **"Queue not found"**: Selected queue does not exist on broker
- **"No messages selected"**: Operation requires message selection
- **"Source and target differ"**: Restore source and target queue/VPN/host are different (user confirmation required)
- **"No message ZIP files found"**: Selected directory doesn't contain valid message ZIP files
- **"Could not extract source metadata"**: Directory path doesn't match expected format

### Limitations

- Maximum page size: Limited by broker capabilities (typically 1000 messages)
- Queue count: No hard limit, but very large counts (>10,000) may impact performance
- Message size: Limited by broker configuration and available memory
- Concurrent operations: Only one message browser window per queue recommended

### Best Practices

1. **Configuration Management**:
   - Use encrypted passwords for production environments
   - Store master password securely (not in config files)
   - Use descriptive broker names for easy identification

2. **Queue Management**:
   - Use filters to reduce queue list size for better performance
   - Default to User queues view to hide system queues
   - Use search to quickly find specific queues

3. **Message Operations**:
   - Verify target queue before bulk operations
   - Use filters to limit scope of operations
   - Download messages before deletion for backup
   - Restore messages to same or different queues as needed
   - Verify source/target queue differences before restoring

4. **Performance**:
   - Use appropriate page sizes (20-50 messages typically optimal)
   - Filter queues when dealing with large broker environments
   - Close unused message browser windows

5. **Security**:
   - Never commit plain text passwords to version control
   - Use encrypted passwords for all production configs
   - Rotate master passwords periodically
   - Limit access to configuration files

---

## Appendix

### Version History

- **v2.1.3**: Added message restore functionality and improved operation logging
- **v2.1.2**: Multi-broker support, filtering, and password encryption
- **v2.0.2**: SMF error handling improvements
- **v2.0.0**: Major UI improvements and cross-platform compatibility

### Support

This tool is NOT a Solace supported product. It has been created by Solace's professional services team to augment Solace products.

For issues, feature requests, or questions, contact the development team.

### License

[License information to be added]

---

**End of User Guide and Reference Manual**

