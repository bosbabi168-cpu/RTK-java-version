magical_net = {
	on_drop = async(function(player)
		if player.mapTitle == "Windy Summit" and player.quest["wind_armor"] ~= 0 and player.quest[
			"min_kawlana"
		] == 1 then
			if player:hasDuration("kawlanas_guard") then
				player:addItem("captured_wind", 1)
				player:removeItem("magical_net", 1, 1)
				player.fakeDrop = 1
				player.quest["kawlana_used"] = 0
				player.quest["kawlana_dropped"] = 0

				player:dialogSeq(
					{
						t,
						"Angin itu menjerit saat kau menjaringnya, tetapi dengan perlindungan Kawlana-mu, lukanya sedikit.",
						"Kau cepat membungkus angin itu dalam jaring. Sekarang tinggal mencari orang yang bisa menenunnya jadi zirah hebat!"
					},
					0
				)
			end
		end
	end),
}
