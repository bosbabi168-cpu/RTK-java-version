purple_potion = {
	use = function(player)
		if player:hasDuration("purple_potion") then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end
		player:setDuration("purple_potion", 300000)
		player:removeItem("purple_potion", 1)
	end
}
