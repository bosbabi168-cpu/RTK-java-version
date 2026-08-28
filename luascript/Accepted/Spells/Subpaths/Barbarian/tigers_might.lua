tigers_might = {
	cast = function(player)
		local magic = 30

		if (not player:canCast(1, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if player:checkIfCast(mights) then
			player:sendMinitext("Mantra lain sejenis itu sedang bekerja.")
			return
		end

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		player:sendMinitext("Kau merapal Tiger's might.")
		player:setDuration("tigers_might", 625000)
		player:playSound(12)
		player:sendAnimation(11)
		player:calcStat()
	end,
	recast = function(player)
		player.might = player.might + 3
		player:sendStatus()
	end,
	uncast = function(player)
		player.might = player.might - 3
		player:sendStatus()
	end,

	requirements = function(player)
		local level = 99
		local items = {"antler", "bears_liver", "tiger_meat", 0}
		local itemAmounts = {5, 10, 10, 5000}
		local desc = "Fill with the strength of a tiger."
		return level, items, itemAmounts, desc
	end
}
