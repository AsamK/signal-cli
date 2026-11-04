Set sh = CreateObject("WScript.Shell")
Set fso  = CreateObject("Scripting.FileSystemObject")

' Always locate signal-cli.bat relative to this script, no matter what folder
' the .vbs is double-clicked from.
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
batPath   = scriptDir & "\signal-cli.bat"

' Load the phone number from an adjacent text file.
numberFile = scriptDir & "\signal-number.txt"
If Not fso.FileExists(numberFile) Then
    WScript.Echo "Missing phone number file: " & numberFile
    WScript.Quit 1
End If
Set ts = fso.OpenTextFile(numberFile, 1)  ' 1 = ForReading
If ts.AtEndOfStream Then
    phoneNumber = ""
Else
    phoneNumber = Trim(ts.ReadLine())
End If
ts.Close

If phoneNumber = "" Then
    WScript.Echo "Phone number file is empty: " & numberFile
    WScript.Quit 1
End If

' 0 = hidden window, False = do not wait for the daemon to exit
cmdLine = batPath & " --account " & phoneNumber & " daemon --http"
sh.Run cmdLine, 0, False

Set sh = Nothing