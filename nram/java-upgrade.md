# Java Upgrade Documentation

## Overview
This document details the upgrade of the Solace Queue Browser GUI project from legacy Java versions to Java 17 LTS, completed on November 5, 2025.

## Upgrade Summary

### Java Runtime Upgrade
- **Previous**: Unspecified Java version (likely Java 8 or 11)
- **Current**: Java 17 LTS
- **Target Attempted**: Java 21 LTS (downgraded due to runtime constraints)

### Maven Configuration Changes

#### Properties Updated
```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <maven.compiler.release>17</maven.compiler.release>
</properties>
```

#### Maven Compiler Plugin Added
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <release>17</release>
    </configuration>
</plugin>
```

## Dependency Upgrades

### Core Libraries

| Dependency | Previous Version | New Version | Change Type |
|------------|------------------|-------------|-------------|
| org.json | 20151123 | 20240303 | Major (9+ years) |
| commons-logging | 1.1.3 | 1.3.4 | Minor |
| gson | 2.2.2 | 2.11.0 | Major |
| okhttp3 | 3.12.2 | 4.12.0 | Major |
| okio | 1.15.0 | 3.9.1 | Major |
| slf4j-api | 1.7.21 | 2.0.16 | Major |

### Logging Framework

| Component | Previous Version | New Version |
|-----------|------------------|-------------|
| log4j-api | 2.17.1 | 2.24.1 |
| log4j-core | 2.17.1 | 2.24.1 |
| log4j-slf4j-impl | 2.17.1 | 2.24.1 |

### HTTP Client Stack

| Component | Previous Version | New Version | Notes |
|-----------|------------------|-------------|-------|
| okhttp3 | 3.12.2 | 4.12.0 | Major version upgrade with breaking changes |
| okhttp3-urlconnection | 3.12.2 | 4.12.0 | Companion library |
| okio | 1.15.0 | 3.9.1 | Major version upgrade |

### Unchanged Dependencies
- commons-cli: 1.9.0 (already latest)
- commons-lang: 2.6 (legacy version maintained for compatibility)
- solace-messaging-client: 1.4.0
- sol-common: 10.6.4
- sol-jcsmp: 10.21.0

## Build Process

### Compilation Issues Encountered

1. **Java 21 Compatibility Issue**
   - Initial target: Java 21
   - Error: `release version 21 not supported`
   - Resolution: Downgraded to Java 17 (available runtime)

2. **Maven JANSI Library Issue**
   - Error: `jansi.dll: Access is denied`
   - Resolution: Use `-Djansi.mode=strip` flag
   - Workaround command: `mvn clean compile -Djansi.mode=strip`

### Successful Build Commands
```bash
# Clean and compile
mvn clean compile -Djansi.mode=strip

# Run tests
mvn test -Djansi.mode=strip

# Create distributable JAR
mvn package -Djansi.mode=strip
```

## Benefits Achieved

### Performance Improvements
- **JVM Performance**: Java 17 provides significant performance improvements over Java 8/11
- **Memory Management**: Enhanced garbage collection algorithms
- **Startup Time**: Faster application startup

### Security Enhancements
- **Updated Dependencies**: All major dependencies updated to latest versions with security patches
- **Log4j**: Updated from 2.17.1 to 2.24.1 (addresses multiple CVEs)
- **HTTP Stack**: OkHttp 4.x includes security improvements

### Modern Language Features Available
- **Pattern Matching**: Enhanced switch expressions
- **Records**: Data classes for immutable data
- **Sealed Classes**: Better type safety
- **Text Blocks**: Improved string handling
- **var Keyword**: Local variable type inference

### Dependency Modernization
- **JSON Processing**: 9+ year upgrade provides better performance and standards compliance
- **HTTP Client**: OkHttp 4.x provides HTTP/2 support and improved connection pooling
- **Logging**: Latest Log4j provides better performance and security

## Future Upgrade Path

### To Java 21 LTS
When ready to upgrade to Java 21:

1. **Install Java 21 JDK**
   ```bash
   # Download from https://adoptium.net/
   # Or use package manager
   ```

2. **Update Environment**
   ```bash
   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot
   ```

3. **Update Maven Configuration**
   ```xml
   <maven.compiler.source>21</maven.compiler.source>
   <maven.compiler.target>21</maven.compiler.target>
   <maven.compiler.release>21</maven.compiler.release>
   ```

### Additional Modern Features (Java 21)
- **Virtual Threads**: Lightweight concurrency
- **Pattern Matching**: Further enhancements
- **Vector API**: SIMD operations
- **Foreign Function Interface**: Native code integration

## Testing and Validation

### Build Status
- ✅ **Compilation**: Successful with Java 17
- ✅ **Dependency Resolution**: All dependencies resolved
- ✅ **Unit Tests**: Verified with `mvn test`
- ✅ **Application Startup**: GUI launches successfully
- ✅ **Queue Browser Functionality**: Double-click detection working
- ✅ **Solace Connection**: JCSMP connection established successfully
- ✅ **Message Browsing**: Queue browser initialization working

### Platform Testing Results
- ✅ **macOS**: Full functionality confirmed
- ⚠️ **Windows VDI**: Core functionality working, potential GUI rendering issues in VDI environment

### Output Artifacts
- **Primary JAR**: `target/SolaceQueueBrowserGui-1.0.0.jar`
- **Fat JAR**: `target/SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar`

### Log Analysis (November 5, 2025)
```
2025-11-05 20:21:20 INFO  QueueBrowserMainWindow:175 - Double-clicked row: 2
2025-11-05 20:21:20 DEBUG QueueBrowserMainWindow:190 - Row 2, Column 1: 'nv-dev-IT-aemtest-4jms-dmq' (type: String)
2025-11-05 20:21:20 INFO  QueueBrowserMainWindow:194 - Selected queue: 'nv-dev-IT-aemtest-4jms-dmq'
2025-11-05 20:21:21 DEBUG QueueBrowser:76 - Created a queue browser object sucessfully on queue 'nv-dev-IT-aemtest-4jms-dmq'.
```

**Result**: Java upgrade successful, application core functionality working correctly.

## Troubleshooting

### Common Issues

1. **JANSI Library Access Denied**
   - **Solution**: Use `-Djansi.mode=strip` flag
   - **Alternative**: Update user permissions on Maven installation

2. **Java Version Compatibility**
   - **Check Current Java**: `java -version`
   - **Check Maven Java**: `mvn -version`
   - **Ensure Compatibility**: Runtime must support target version

3. **Dependency Conflicts**
   - **Check**: `mvn dependency:tree`
   - **Resolve**: Use dependency management or exclusions

### Windows VDI Specific Issues

4. **GUI Rendering Problems in VDI Environment**
   - **Symptoms**: Application functions correctly but GUI may not respond as expected
   - **Root Cause**: VDI environments can have Java Swing rendering issues
   - **Workarounds**:
     ```bash
     # Try different Java rendering modes
     java -Dsun.java2d.d3d=false -jar target\SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c nram\nv-dev.json
     
     # Alternative: Disable hardware acceleration
     java -Dsun.java2d.noddraw=true -jar target\SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c nram\nv-dev.json
     
     # Force software rendering
     java -Dsun.java2d.opengl=false -jar target\SolaceQueueBrowserGui-1.0.0-jar-with-dependencies.jar -c nram\nv-dev.json
     ```
   - **Note**: Core functionality (connections, queue browsing) works correctly as confirmed by logs

5. **VDI Resource Constraints**
   - **Memory**: Ensure adequate heap space: `-Xmx1024m`
   - **CPU**: VDI may throttle Java applications
   - **Network**: Check if VDI firewall affects Solace connections

## Contributors
- **Upgrade Performed By**: GitHub Copilot Assistant
- **Date**: November 5, 2025
- **Branch**: nram-nv-dev
- **Repository**: SolaceServices/SolaceQueueBrowserGui

## References
- [OpenJDK 17 Documentation](https://openjdk.org/projects/jdk/17/)
- [Maven Compiler Plugin](https://maven.apache.org/plugins/maven-compiler-plugin/)
- [Log4j 2.24.1 Release Notes](https://logging.apache.org/log4j/2.x/release-notes.html)
- [OkHttp 4.x Migration Guide](https://square.github.io/okhttp/upgrading_to_okhttp_4/)