empty_water_jug = {
	on_drop = function(player, item)
		if player.mapTitle == "Kugnae" and player.x >= 30 and player.x <= 31 and player.y == 30 then
			local t = {graphic = convertGraphic(731, "item"), color = 0}
			player.npcGraphic = t.graphic
			player.npcColor = t.color
			player.dialogType = 0
			player.lastClick = player.ID

			player:addItem("water_jug", 1)
			player:removeItem("empty_water_jug", 1, 1)
			player.fakeDrop = 1
			player:dialogSeq({t, "Kau mengisi kendimu dengan air."}, 0)
		end
	end
}
