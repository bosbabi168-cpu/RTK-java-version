freeze = {
	cast = function(player, target)
		local duration = 60000
		local magicCost = 800
		local damage = 112

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return
		end

		if (target.blType == BL_PC and not player:canPK(target)) or target.blType == BL_NPC then
			player:sendMinitext("Kau tidak bisa menyerang sasaran itu.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(1)
		player:sendMinitext("Kau merapal Freeze.")
		player:sendMinitext("Calling Freeze.")
		target:sendAnimation(52)
		target.attacker = player.ID
		target:removeHealthExtend(damage, 1, 1, 1, 1, 0)
	end
}
