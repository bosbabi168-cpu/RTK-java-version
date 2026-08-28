brown_potion = {
	use = function(player)
		if player:checkIfCast(hardarmors) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:setDuration("harden_armor", 185000)
		player:sendAction(8, 25)
		player:calcStat()
		player:removeItem("brown_potion", 1, 6)
	end
}
