song_of_the_goddess = {
	cast = function(player, target)
		local spellName = "Song of the Goddess"

		local manacost = 1000
		if player.mark == 1 then
			manacost = 3000
		end
		if player.mark == 2 then
			manacost = 6000
		end

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < manacost) then
			player:sendMinitext("Kehendakmu terlalu lemah.")
			return 0
		end
		if (target.state == 1) then
			player:sendMinitext("Kau butuh jenis penawar yang lain.")
			return 0
		end

		target:sendMinitext(player.name .. " merapal " .. spellName .. " padamu.")
		target:setDuration("song_of_the_goddess", 24000, player.ID, 1)
		target:playSound(4)
		player:sendAction(6, 35)
		player.magic = player.magic - manacost
		target:sendStatus()
		player:sendStatus()
	end,
	while_cast = function(target, player)
		local amount = 5000
		if player.mark == 1 then
			amount = 10000
		end
		if player.mark == 2 then
			amount = 20000
		end
		target.attacker = player.ID
		target:addHealthExtend(amount, 0, 0, 0, 0, 0)
		target:playSound(4)
		target:sendAnimation(66)
		target:sendStatus()
	end,
}
