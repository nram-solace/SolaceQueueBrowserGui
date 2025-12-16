# Solace Queue Browser GUI

![Project Logo](./SolaceQueueBrowserGui/config/queueBrowserIcon.png "Queue Browser Logo")

A desktop application for browsing and managing Solace message queues through an intuitive graphical interface.

![Screenshot](./screenshot.png "Screenshot")

## Features

- **Queue Browsing**: View messages in Solace queues with pagination and caching for performance
- **Message Inspection**: Examine message content, properties, and metadata
- **Message Operations**: Delete, move, and transfer messages between queues
- **Drag & Drop**: Intuitive drag and drop operations for moving messages
- **Filtering & Search**: Filter and search through queue messages
- **Bulk Operations**: Select and operate on multiple messages at once
- **Multi-Broker Support**: Works with both Solace Cloud and on-premise brokers

## Requirements

- Java 8 or higher
- Access to a Solace message broker
- Admin credentials for SEMP operations
- Client credentials for messaging operations

## Quick Start

### Building the Application

Use the provided build script:
```bash
./SolaceQueueBrowserGui/build.sh
```

Or build manually with Maven:
```bash
mvn clean package
```

### Running the Application

Use the provided run script (uses sample config by default):
```bash
./SolaceQueueBrowserGui/run.sh [config-file]
```

Or run directly:
```bash
java -jar target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c=path/to/your-config.json
```

### Configuration

Create a JSON configuration file with your broker connection details. Sample configurations are provided in the `SolaceQueueBrowserGui/sample/` directory:

- `sampleBrowserConfig.json` - Template for Solace Cloud
- `sample-local.json`        - Local broker example  
- `sample-cloud.json`        - Solace Cloud deployment example

Configuration format:
```json
{
  "eventBroker": {
    "name": "My Solace Broker",
    "sempHost": "https://your-broker:943/SEMP/v2/config",
    "sempAdminUser": "admin-username",
    "sempAdminPw": "admin-password",
    "msgVpnName": "your-vpn-name",
    "messagingHost": "tcps://your-broker:55443",
    "messagingClientUsername": "client-username", 
    "messagingPw": "client-password"
  },
  "downloadFolder": "./downloads"
}
```

### Getting Solace Cloud Credentials

1. **Admin credentials**: In Solace Cloud console → Your Broker → Manage → SEMP - REST API section
2. **Messaging credentials**: In Solace Cloud console → Your Broker → Connect → Solace Messaging section

## Usage

### Basic Workflow

1. **Launch** the application with your configuration file
2. **Browse Queues** - The main window shows available queues in your VPN
3. **Select a Queue** - Double-click or select a queue to browse its messages
4. **View Messages** - Browse through messages with pagination controls
5. **Inspect Details** - Click on messages to view content and properties
6. **Perform Operations** - Use right-click menus or drag & drop for message operations

### Key Operations

- **View Message Content**: Double-click any message to see full details
- **Delete Messages**: Select messages and use delete operations
- **Move Messages**: Drag messages from one queue to another
- **Filter Messages**: Use search and filter controls to find specific messages
- **Bulk Operations**: Select multiple messages using Ctrl+click or Shift+click

## Troubleshooting

### Connection Issues
- Verify your broker URLs and ports are correct
- Ensure your credentials have appropriate permissions
- Check that your VPN name is accurate

### Authentication Problems
- Admin credentials need SEMP access for queue management
- Client credentials need messaging permissions for queue browsing
- Verify password accuracy and account status

### Performance Tips
- The application uses caching to improve browsing performance
- For large queues, use filtering to reduce the dataset
- Pagination helps manage memory usage with large message sets

## Important Disclaimer

This tool is NOT a Solace supported product. It has been created by Solace's professional services team to augment Solace products.

**Security Note**: Keep your configuration file secure and never commit it to version control with real credentials.

## Logs

Application logs are written to `logs/browser.log` and can help diagnose connection or operational issues.

## Feedback

Send feature requests, defects and comments to mike.obrien@solace.com


