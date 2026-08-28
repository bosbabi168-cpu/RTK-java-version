nagnangWarriorShieldTotem = {
	onClick = function(player)
		if player:hasLegend("nagnang_warrior_trial") then
			return
		end

		local t = {graphic = convertGraphic(165, "monster"), color = 0}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = 0

		local mobs = {
			"red_deer",
			"red_doe",
			"red_rabbit",
			"blue_deer",
			"blue_doe",
			"blue_rabbit"
		}

		local mobFound = false
		for i = 1, #mobs do
			if player:killCount(mobs[i]) >= 1 then
				mobFound = true
			end
		end

		if (mobFound or player.quest["needsForgiveWarriorShieldTotem"] == 1) then
			player.quest["needsForgiveWarriorShieldTotem"] = 1
			player:sendMinitext("Kau menyentuh patung itu berkali-kali, tetapi tampaknya tidak terjadi apa-apa. Kau mendengar suara samar menyebutmu pembunuh...")
			player:sendMinitext("Mungkin seharusnya kau tidak membunuh binatang merah dan biru di gua itu.")

			for i = 1, #mobs do
				player:flushKills(mobs[i])
			end

			player:warp(361, 18, 6)

			return
		end

		player:dialogSeq(
			{
				t,
				"Saat kau menyentuh patung perkasa itu, ia seakan hidup!",
				"Ah, manusia fana, kau berani memasuki guaku untuk menghadapiku? Kau prajurit yang berani dan layak.",
				"Kau menepati janjimu dan tidak melukai satu pun makhluk merah dan biru. Kau menunjukkan kehormatan dan kecakapan.",
				"Kau akan kuganjar. Ambil perisai ini, semoga ia melindungimu dalam pertempuran mendatang. Hanya ini satu-satunya yang akan kuberikan padamu.",
				"Patung itu kembali menjadi batu, dan kau melihat sebuah perisai di anak tangga lalu memungutnya."
			},
			1
		)

		player.quest["nagnang_warrior_trial"] = 0
		player:addLegend(
			"Completed Nangen Warrior Trial (" .. curT() .. ")",
			"nagnang_warrior_trial",
			9,
			128
		)
		player:addItem("tall_shield", 1, 0, player.ID)
	end
}
