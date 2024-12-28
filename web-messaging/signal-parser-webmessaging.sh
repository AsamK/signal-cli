#!/bin/bash
# This script was authored by Aaron Surina in December 2024.
# This script was created to allow my children to communicate with me while with their mother and her boyfriend
# This script is to prevent further abuse of parental rights and to curb the massive attempts at alienating a fit and loving father from his children.
# Thank you for participating in the revolution.
# I dedicate this work to the resilience and perserverance of my two sons David and Andrew.
# I believe in you and I love you forever and always.  
# You can do anything you set your mind to.   
# 
# End family court corruption.  Hold false witness accountable.
# Disbarr unethical attorneys in family courts.
# 
export SIGNAL_NUMBER="+12085551212"
signal-cli -a $SIGNAL_NUMBER -v receive \
|awk 'BEGIN { OFS=" - "; }
    /Envelope from:/ {
        from = $3; # Capture the sender phone number
    }
    /Timestamp:/ && /2024-/ {
        timestamp = substr($0, index($0, "2024-")); # Extract human-readable timestamp
    }
    /Body:/ {
        body = substr($0, index($0, "Body:") + 6); # Capture the message body
        print timestamp, from, body; # Print timestamp, sender, and message body
        from=""; body=""; timestamp=""; # Reset values
    }' |tee -a /var/log/signal.log &
