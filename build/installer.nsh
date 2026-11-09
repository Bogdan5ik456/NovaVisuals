!macro customUnInstall
  ${ifNot} ${Silent}
    MessageBox MB_YESNO|MB_ICONQUESTION "Видалити також дані Nova Visuals (світи, моди, налаштування гри)?$\r$\nПапка: $APPDATA\.nova-visuals" /SD IDNO IDNO nv_keep_data
      RMDir /r "$APPDATA\.nova-visuals"
    nv_keep_data:
  ${endIf}
!macroend
