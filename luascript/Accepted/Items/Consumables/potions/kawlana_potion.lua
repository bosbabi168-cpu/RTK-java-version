kawlana_potion = {
	use = async(function(player)
		if player.mapTitle ~= "Windy Summit" then
			local health = 200

			player:sendAction(8, 25)
			player.attacker = player.ID
			player:addHealthExtend(health, 0, 0, 0, 0, 0)
			player:removeItem("kawlana_potion", 1, 1)

			player:sendMinitext("Daya hidup dalam ramuan kawlana-mu sudah menguap.")

			if player.health == player.maxHealth then
				player:sendMinitext("Kau merasa kenyang.")
			end
		end

		if player.mapTitle == "Windy Summit" and player.quest["wind_armor"] ~= 0 and player.quest[
			"min_kawlana"
		] == 1 and player.quest["kawlana_dropped"] == 1 then
			player:removeItem("kawlana_potion", 1)
			player.quest["kawlana_used"] = 1
			player:setDuration("kawlanas_guard", 15000)
			player:dialogSeq(
				{
					t,
					"Kau cepat meminum Kawlana, dan tubuhmu menguat melawan angin. Inilah kesempatanmu!"
				},
				0
			)
		end
	end),

	on_drop = async(function(player)
		if player.mapTitle == "Windy Summit" and player.quest["wind_armor"] ~= 0 and player.quest[
			"min_kawlana"
		] == 1 then
			player.quest["kawlana_dropped"] = 1
			player:removeItem("kawlana_potion", 1, 6)
			player.fakeDrop = 1
			player:dialogSeq(
				{
					t,
					"Kau menjatuhkan kawlana ke tanah, dan kau merasakan angin makin kuat saat mendekat.",
					"Mereka menguat, dan angin mulai mencambuk kulitmu. Tanpa perlindungan kau tidak akan selamat."
				},
				0
			)
		end
	end)
}
