; Nova Visuals: повне видалення при деінсталяції.
; При автооновленні (isUpdated) нічого не чіпаємо, щоб не втратити світи й налаштування.
!macro customUnInstall
  ${ifNot} ${isUpdated}
    RMDir /r "$APPDATA\.nova-visuals"                          ; гра: Java, версії, моди, світи, скіни, журнали
    RMDir /r "$APPDATA\Nova Visuals"                           ; налаштування лаунчера й токени входу
    RMDir /r "$APPDATA\nova-visuals-launcher"
    RMDir /r "$LOCALAPPDATA\nova-visuals-launcher-updater"     ; кеш автооновлення
    RMDir /r "$LOCALAPPDATA\Nova Visuals"
  ${endIf}
!macroend
