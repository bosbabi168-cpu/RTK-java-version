nagnang_compass = {
	use = function(player)
		local baseMap = getNagnangShieldQuestBaseMap(player)

		local t = {graphic = convertGraphic(715, "item"), color = 0}

		player:sendMinitext("Kau merapal Find path.")

		if player.m == baseMap then
			player:dialogSeq({t, "Jarumnya menunjuk ke timur."}, 0)
		elseif player.m == baseMap + 1 then
			player.quest["used_compass"] = 1
			player:dialogSeq({t, "Jarumnya menunjuk ke timur."}, 0)
		elseif player.m == baseMap + 2 then
			player:dialogSeq({t, "Jarumnya menunjuk ke barat."}, 0)
		elseif player.m == baseMap + 3 then
			player:dialogSeq({t, "Jarumnya menunjuk ke barat."}, 0)
		elseif player.m == baseMap + 4 then
			player:dialogSeq({t, "Jarumnya menunjuk ke selatan."}, 0)
		elseif player.m == baseMap + 5 then
			player:dialogSeq({t, "Jarumnya menunjuk ke utara."}, 0)
		else
			player:dialogSeq({t, "Jarumnya menunjuk ke Nagnang."}, 0)
		end
	end
}
