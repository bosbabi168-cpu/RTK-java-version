stagger = {
	cast = function(player)
		local duration = 60000
		local magicCost = 1

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(43)
		player:calcStat()
		player:setDuration("stagger", duration)
		player:sendMinitext("Kau ketakutan luar biasa.")
		player:sendMinitext("Kau merapal Stagger.")
	end,

	while_cast = function(player)
		player.drunk = 1
		player:sendStatus()
	end,

	recast = function(player)
		player.drunk = 1
		player:sendStatus()
	end,

	uncast = function(player)
		player.drunk = 0
		player:sendStatus()
		player:sendMinitext("Kau tenang kembali dan pulih ke keadaan semula.")
	end
}
