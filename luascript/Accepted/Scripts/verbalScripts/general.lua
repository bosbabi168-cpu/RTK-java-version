verbalScriptGeneral = function(player, npc, speech)
	Tools.configureDialog(player, npc)

	if string.match(speech, "what's your name") or string.match(
		speech,
		"what is your name"
	) then
		npc:talk(0, "Halo, namaku " .. npc.name .. ".")
	end
end
