yellow_scroll = {
	use = function(player)
		if player.warpOut == 0 then
			player:sendMinitext("Tidak bisa berpindah keluar.")
			return
		end

		if player:canPK(player) then
			-- pvp maps
			player:sendMinitext("Tidak bisa berpindah keluar.")
			return
		end

		if player.m == 666 then
			return
		end

		if player.state == 1 then
			player:sendMinitext("Kau butuh tubuh jasmani untuk memakai gulungan kuning ini.")
			return
		end

		player:removeItem("yellow_scroll", 1)
		player:returnFunc()
	end
}
