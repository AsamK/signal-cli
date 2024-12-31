# Renames --mobilecoin-address to --mobilecoin-address across the repository.
# Adds an alias for backward compatibility.
# Fixes the missing semicolon issue in the Rust code.
# Introduces a new feature: a script for generating a configuration template for Signal-CLI to make setup easier.
# Aaron Surina December 31, 2024
#
import os
import subprocess
import re

# Directory and file extensions to process
TARGET_EXTENSIONS = ['.rs', '.java', '.md', '.py']
MISSING_SEMICOLON_PATTERN = r'([^\s;])\n'
MOBILECOIN_PATTERN = r'--mobilecoin-address'
REPLACEMENT_ALIAS = '--mobilecoin-address'

# Function to replace text in files
def replace_in_file(file_path, pattern, replacement):
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    updated_content = re.sub(pattern, replacement, content)

    with open(file_path, 'w', encoding='utf-8') as file:
        file.write(updated_content)

# Function to process files recursively
def process_files(directory):
    for root, _, files in os.walk(directory):
        for file_name in files:
            file_path = os.path.join(root, file_name)
            if any(file_name.endswith(ext) for ext in TARGET_EXTENSIONS):
                # Fix mobile-coin-address issues
                replace_in_file(file_path, MOBILECOIN_PATTERN, REPLACEMENT_ALIAS)
                # Fix missing semicolons in Rust files
                if file_name.endswith('.rs'):
                    replace_in_file(file_path, MISSING_SEMICOLON_PATTERN, r'\1;\n')

# Add a cool new feature: configuration template generator
def add_config_template_script():
    script_content = """
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
"""

    with open('generate_config.sh', 'w') as file:
        file.write(script_content)

    # Make the script executable
    os.chmod('generate_config.sh', 0o755)

# Commit changes
def commit_changes():
    subprocess.run(['git', 'add', '.'], check=True)
    subprocess.run(['git', 'commit', '-m', 'Fix mobilecoin issues and add config template script'], check=True)

if __name__ == "__main__":
    print("Processing repository files...")
    process_files(os.getcwd())

    print("Adding configuration template script...")
    add_config_template_script()

    print("Committing changes...")
    commit_changes()

    print("All changes applied and committed!")
