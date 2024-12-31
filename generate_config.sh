
#!/bin/bash

echo "Generating Signal-CLI configuration template..."
CONFIG_TEMPLATE="config-template.yml"

cat <<EOL > $CONFIG_TEMPLATE
# Signal-CLI Configuration Template
# Replace values with your own preferences

signal-cli:
  user: "<your-phone-number>"
  storage-path: "<path-to-storage>"
  message-retry: 3
  enable-notifications: true

# Add more options as needed
EOL

echo "Configuration template created at $CONFIG_TEMPLATE"
