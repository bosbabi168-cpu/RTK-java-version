sanctuary = {
	cast = function(player)
		local duration = 300000

		if player:checkIfCast(sanctuaries) then
			return
		end

		player:sendMinitext("Kau menemukan tempat perlindungan.")
		player.deduction = player.deduction -.5

		player:setDuration("sanctuary", duration)
		player:sendAnimation(2, 0)

		if player.blType == BL_PC then
			player:sendMinitext(player.name .. " merapal Sanctuary padamu.")
		end
	end,

	recast = function(player)
		player.deduction = player.deduction -.5
	end,

	uncast = function(player)
		player.deduction = player.deduction +.5
		player:calcStat()
	end
}
