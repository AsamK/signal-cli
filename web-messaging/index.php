
<?php
session_start();
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Set timezone for timestamps
date_default_timezone_set('America/Los_Angeles');

// Passcode to access the site
$required_passcode = '6262';

// Paths for message files and Signal-CLI configuration
$message_file = '/var/www/html/messages.txt'; // Ensure this path is correct and writable
$signal_log_file = '/var/log/signal.log'; // Log file for Signal messages
$signalCliPath = '/usr/bin/signal-cli'; // Adjust this path
$signalNumber = '+12085551212'; // Replace with your Signal number
$default_recipient_number = '+17075551212'; // Default recipient number

// Handle logout
if (isset($_GET['logout'])) {
    unset($_SESSION['authenticated']);
    session_destroy();
    header('Location: /index.php');
    exit;
}

// Handle passcode submission
if (isset($_POST['passcode'])) {
    $entered_code = trim($_POST['passcode']);
    if ($entered_code === $required_passcode) {
        $_SESSION['authenticated'] = true;
        header('Location: /index.php'); // Reload to avoid resubmission
        exit;
    } else {
        $error = "Incorrect passcode. Please try again.";
    }
}

// Function to fetch local messages from messages.txt
function get_site_messages($file_path) {
    $messages = [];
    if (file_exists($file_path)) {
        $messages_raw = file_get_contents($file_path);
        $messages_lines = explode("\n", $messages_raw);
        foreach ($messages_lines as $line) {
            if (trim($line)) {
                $parts = explode('|', $line, 2);
                if (count($parts) === 2) {
                    $messages[] = [
                        'timestamp' => $parts[0],
                        'message' => $parts[1],
                    ];
                }
            }
        }
    }
    return $messages;
}

// Function to fetch Signal messages from the log file
function get_signal_messages($log_file) {
    $messages = [];
    if (file_exists($log_file)) {
        $messages_raw = file_get_contents($log_file);
        $messages_lines = explode("\n", $messages_raw);
        foreach ($messages_lines as $line) {
            if (trim($line)) {
                $messages[] = $line;
            }
        }
    }
    return $messages;
}

// Function to send a Signal message
function send_signal_message($recipient_number, $message, $signalCliPath, $signalNumber) {
    $recipient_number = escapeshellarg($recipient_number);
    $message = escapeshellarg($message);
    $command = "$signalCliPath -u $signalNumber send -m $message $recipient_number";
    exec($command, $output, $return_var);
    return $return_var === 0;
}

// Handle posting a new local message
if (isset($_SESSION['authenticated']) && $_SESSION['authenticated'] === true && isset($_POST['message'])) {
    $message = trim($_POST['message']);
    if (!empty($message) && substr($message, -3) === '123') {
        $cleaned_message = rtrim($message, '123');
        $timestamp = date('Y-m-d H:i:s');
        $entry = "$timestamp|$cleaned_message\n";
        file_put_contents($message_file, $entry, FILE_APPEND | LOCK_EX);
    }
}

// Handle sending a Signal message
$signal_message_status = '';
if (isset($_SESSION['authenticated']) && $_SESSION['authenticated'] === true && isset($_POST['send_signal'])) {
    $recipient_number = $_POST['recipient_number'] ?? $default_recipient_number;
    $message = $_POST['signal_message'] ?? '';
    if (send_signal_message($recipient_number, $message, $signalCliPath, $signalNumber)) {
        $signal_message_status = "Message sent successfully to $recipient_number.";
    } else {
        $signal_message_status = "Failed to send the message.";
    }
}

// Fetch messages for display
$site_messages = get_site_messages($message_file);
$signal_messages = get_signal_messages($signal_log_file);
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Family Center</title>
    <style>
        body {
            background-color: #000;
            color: #fff;
            font-family: Arial, sans-serif;
            margin: 20px;
        }
        .container {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
        }
        .column {
            padding: 10px;
            background: #111;
            border-radius: 5px;
        }
        .message-entry {
            margin-bottom: 10px;
            padding: 10px;
            background: #222;
            border-radius: 5px;
        }
        h1, label, .error, .status {
            color: #fff;
        }
        .error {
            color: #f00;
        }
        .status {
            color: #0f0;
        }
    </style>
</head>
<body>
    <h1>Family Center</h1>

    <?php if (!isset($_SESSION['authenticated']) || $_SESSION['authenticated'] !== true): ?>
        <?php if (isset($error)): ?>
            <div class="error"><?php echo htmlspecialchars($error); ?></div>
        <?php endif; ?>
        <form method="post">
            <label for="passcode">Enter Passcode:</label>
            <input type="password" name="passcode" id="passcode" required>
            <button type="submit">Login</button>
        </form>
    <?php else: ?>
        <div class="logout-button">
            <form method="get">
                <button type="submit" name="logout">Logout</button>
            </form>
        </div>

        <div class="container">
            <div class="column">
                <h2>Messages Posted Locally:</h2>
                <?php if (!empty($site_messages)): ?>
                    <?php foreach ($site_messages as $msg): ?>
                        <div class="message-entry">
                            <strong><?php echo htmlspecialchars($msg['timestamp']); ?></strong><br>
                            <?php echo nl2br(htmlspecialchars($msg['message'])); ?>
                        </div>
                    <?php endforeach; ?>
                <?php else: ?>
                    <p>No messages posted yet.</p>
                <?php endif; ?>
            </div>

            <div class="column">
                <h2>Signal Messages:</h2>
                <?php if (!empty($signal_messages)): ?>
                    <?php foreach ($signal_messages as $msg): ?>
                        <div class="message-entry">
                            <?php echo nl2br(htmlspecialchars($msg)); ?>
                        </div>
                    <?php endforeach; ?>
                <?php else: ?>
                    <p>No Signal messages received yet.</p>
                <?php endif; ?>
            </div>
        </div>

        <form method="post">
            <label for="message">Post a Local Message (end with '123'):</label>
            <textarea name="message" id="message" rows="4" required></textarea>
            <button type="submit">Post Message</button>
        </form>

        <form method="post">
            <label for="recipient_number">Signal Recipient Number:</label>
            <input type="text" name="recipient_number" id="recipient_number" value="<?php echo htmlspecialchars($default_recipient_number); ?>" required>
            <label for="signal_message">Signal Message:</label>
            <textarea name="signal_message" id="signal_message" rows="4"></textarea>
            <button type="submit" name="send_signal">Send Signal Message</button>
        </form>

        <?php if (!empty($signal_message_status)): ?>
            <div class="status"><?php echo htmlspecialchars($signal_message_status); ?></div>
        <?php endif; ?>
    <?php endif; ?>
</body>
</html>
